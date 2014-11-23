package org.hive2hive.core.processes.share.read;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.hive2hive.core.H2HJUnitTest;
import org.hive2hive.core.exceptions.GetFailedException;
import org.hive2hive.core.exceptions.IllegalFileLocation;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.model.FolderIndex;
import org.hive2hive.core.model.Index;
import org.hive2hive.core.model.PermissionType;
import org.hive2hive.core.model.UserPermission;
import org.hive2hive.core.model.versioned.UserProfile;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.processes.ProcessFactory;
import org.hive2hive.core.security.UserCredentials;
import org.hive2hive.core.utils.FileTestUtil;
import org.hive2hive.core.utils.H2HWaiter;
import org.hive2hive.core.utils.NetworkTestUtil;
import org.hive2hive.core.utils.TestExecutionUtil;
import org.hive2hive.core.utils.TestFileEventListener;
import org.hive2hive.core.utils.UseCaseTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A folder is shared with {@link PermissionType#READ} permission. Tests moving files and folder within a
 * shared folder.
 * 
 * @author Seppi
 */
public class SharedFolderWithReadPermissionMoveInternalTest extends H2HJUnitTest {

	private static final int CHUNK_SIZE = 1024;
	private static final int maxNumChunks = 2;

	private static ArrayList<NetworkManager> network;
	private static NetworkManager nodeA;
	private static NetworkManager nodeB;

	private static File rootA;
	private static File rootB;
	private static File sharedFolderA;
	private static File sharedFolderB;
	private static File subFolder1A;
	private static File subFolder1B;
	private static File subFolder2A;
	private static File subFolder2B;

	private static UserCredentials userA;
	private static UserCredentials userB;

	private static TestFileEventListener eventsAtA;
	private static TestFileEventListener eventsAtB;

	/**
	 * Setup network. Setup two users with each one client, log them in.
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void initTest() throws Exception {
		testClass = SharedFolderWithReadPermissionDeleteTest.class;
		beforeClass();

		logger.info("Setup network.");
		network = NetworkTestUtil.createNetwork(6);
		nodeA = network.get(0);
		nodeB = network.get(1);

		logger.info("Create user A.");
		rootA = FileTestUtil.getTempDirectory();
		userA = generateRandomCredentials();
		logger.info("Register and login user A.");
		UseCaseTestUtil.registerAndLogin(userA, nodeA, rootA);

		eventsAtA = new TestFileEventListener(nodeA);
		nodeA.getEventBus().subscribe(eventsAtA);

		logger.info("Create user B.");
		rootB = FileTestUtil.getTempDirectory();
		userB = generateRandomCredentials();
		logger.info("Register and login user B.");
		UseCaseTestUtil.registerAndLogin(userB, nodeB, rootB);

		eventsAtB = new TestFileEventListener(nodeB);
		nodeB.getEventBus().subscribe(eventsAtB);

		sharedFolderA = new File(rootA, "sharedfolder");
		sharedFolderA.mkdirs();
		logger.info("Upload folder '{}' from A.", sharedFolderA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, sharedFolderA);

		logger.info("Share folder '{}' with user B giving read permission.", sharedFolderA.getName());
		UseCaseTestUtil.shareFolder(nodeA, sharedFolderA, userB.getUserId(), PermissionType.READ);
		sharedFolderB = new File(rootB, sharedFolderA.getName());
		waitTillSynchronized(sharedFolderB);

		subFolder1A = new File(sharedFolderA, "subfolder1");
		subFolder1A.mkdir();
		logger.info("Upload a new subfolder '{}'.", subFolder1A);
		UseCaseTestUtil.uploadNewFile(nodeA, subFolder1A);
		subFolder1B = new File(sharedFolderB, subFolder1A.getName());
		waitTillSynchronized(subFolder1B);

		subFolder2A = new File(sharedFolderA, "subfolder2");
		subFolder2A.mkdir();
		logger.info("Upload a new subfolder '{}'.", subFolder2A);
		UseCaseTestUtil.uploadNewFile(nodeA, subFolder2A);
		subFolder2B = new File(sharedFolderB, subFolder2A.getName());
		waitTillSynchronized(subFolder2B);
	}

	@Test
	public void testSynchronizeAddFileAtAMoveToSubfolderAtA() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File fileFromAAtA = FileTestUtil.createFileRandomContent("file1FromA", new Random().nextInt(maxNumChunks) + 1,
				sharedFolderA, CHUNK_SIZE);
		logger.info("Upload a new file '{}' at A.", fileFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, fileFromAAtA);

		logger.info("Wait till new file '{}' gets synchronized with B.", fileFromAAtA.getName());
		File fileFromAAtB = new File(sharedFolderB, fileFromAAtA.getName());
		waitTillSynchronized(fileFromAAtB);

		File movedFileFromAAtA = new File(subFolder1A, fileFromAAtA.getName());
		logger.info("Move file '{}' at A into shared subfolder '{}'.", fileFromAAtA.getName(), subFolder1A);
		FileUtils.moveFile(fileFromAAtA, movedFileFromAAtA);
		UseCaseTestUtil.moveFile(nodeA, fileFromAAtA, movedFileFromAAtA);

		logger.info("Wait till new moved file '{}' gets synchronized with B.", fileFromAAtA.getName());
		File movedFileFromAAtB = new File(subFolder1B, fileFromAAtA.getName());
		waitTillSynchronized(movedFileFromAAtB);
		compareFiles(movedFileFromAAtA, movedFileFromAAtB);
		checkIndexAfterMoving(fileFromAAtA, fileFromAAtB, movedFileFromAAtA, movedFileFromAAtB);
	}

	@Test
	public void testSynchronizeAddFileAtATryToMoveToSubfolderAtB() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File fileFromAAtA = FileTestUtil.createFileRandomContent("file2FromA", new Random().nextInt(maxNumChunks) + 1,
				sharedFolderA, CHUNK_SIZE);
		logger.info("Upload a new file '{}' at A.", fileFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, fileFromAAtA);

		logger.info("Wait till new file '{}' gets synchronized with B.", fileFromAAtA.getName());
		File fileFromAAtB = new File(sharedFolderB, fileFromAAtA.getName());
		waitTillSynchronized(fileFromAAtB);

		File movingFileAtB = new File(subFolder1B, fileFromAAtA.getName());
		logger.info("Try to move file '{}' at B into shared subfolder '{}'.", fileFromAAtA.getName(), subFolder1B);
		TestExecutionUtil.executeProcessTillFailed(ProcessFactory.instance().createMoveFileProcess(fileFromAAtB,
				movingFileAtB, nodeB));
		checkIndexAfterTryingToMove(fileFromAAtA, fileFromAAtB);
	}

	@Test
	public void testSynchronizeAddFolderAtAMoveToSubfolderAtA() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File folderFromAAtA = new File(sharedFolderA, "folder1FromA");
		folderFromAAtA.mkdir();
		logger.info("Upload a new folder '{}' at A.", folderFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, folderFromAAtA);

		logger.info("Wait till new folder '{}' gets synchronized with B.", folderFromAAtA.getName());
		File folderFromAAtB = new File(sharedFolderB, folderFromAAtA.getName());
		waitTillSynchronized(folderFromAAtB);

		File movedFolderFromAAtA = new File(subFolder1A, folderFromAAtA.getName());
		logger.info("Move folder '{}' at A into shared subfolder '{}'.", folderFromAAtA.getName(), subFolder1A);
		FileUtils.moveDirectory(folderFromAAtA, movedFolderFromAAtA);
		UseCaseTestUtil.moveFile(nodeA, folderFromAAtA, movedFolderFromAAtA);

		logger.info("Wait till new moved folder '{}' gets synchronized with B.", folderFromAAtA.getName());
		File movedFolderFromAAtB = new File(subFolder1B, folderFromAAtA.getName());
		waitTillSynchronized(movedFolderFromAAtB);
		compareFiles(movedFolderFromAAtA, movedFolderFromAAtB);
		checkIndexAfterMoving(folderFromAAtA, folderFromAAtB, movedFolderFromAAtA, movedFolderFromAAtB);
	}

	@Test
	public void testSynchronizeAddFolderAtATryToMoveToSubfolderAtB() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File folderFromAAtA = new File(sharedFolderA, "folder2FromA");
		folderFromAAtA.mkdir();
		logger.info("Upload a new folder '{}' at A.", folderFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, folderFromAAtA);

		logger.info("Wait till new folder '{}' gets synchronized with B.", folderFromAAtA.getName());
		File folderFromAAtB = new File(sharedFolderB, folderFromAAtA.getName());
		waitTillSynchronized(folderFromAAtB);

		File movingFolderAtB = new File(subFolder1B, folderFromAAtA.getName());
		logger.info("Try to move folder '{}' at B into shared subfolder '{}'.", folderFromAAtA.getName(), subFolder1B);
		TestExecutionUtil.executeProcessTillFailed(ProcessFactory.instance().createMoveFileProcess(folderFromAAtB,
				movingFolderAtB, nodeB));
		checkIndexAfterTryingToMove(folderFromAAtA, folderFromAAtB);
	}

	@Test
	public void testSynchronizeAddSubfileAtAMoveToFolderAtA() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File fileFromAAtA = FileTestUtil.createFileRandomContent("subfile1FromA", new Random().nextInt(maxNumChunks) + 1,
				subFolder1A, CHUNK_SIZE);
		logger.info("Upload a new file '{}' at A.", fileFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, fileFromAAtA);

		logger.info("Wait till new file '{}' gets synchronized with B.", fileFromAAtA.getName());
		File fileFromAAtB = new File(subFolder1B, fileFromAAtA.getName());
		waitTillSynchronized(fileFromAAtB);

		File movedFileFromAAtA = new File(sharedFolderA, fileFromAAtA.getName());
		logger.info("Move file '{}' at A into shared subfolder '{}'.", fileFromAAtA.getName(), sharedFolderA);
		FileUtils.moveFile(fileFromAAtA, movedFileFromAAtA);
		UseCaseTestUtil.moveFile(nodeA, fileFromAAtA, movedFileFromAAtA);

		logger.info("Wait till new moved file '{}' gets synchronized with B.", fileFromAAtA.getName());
		File movedFileFromAAtB = new File(sharedFolderB, fileFromAAtA.getName());
		waitTillSynchronized(movedFileFromAAtB);
		compareFiles(movedFileFromAAtA, movedFileFromAAtB);
		checkIndexAfterMoving(fileFromAAtA, fileFromAAtB, movedFileFromAAtA, movedFileFromAAtB);
	}

	@Test
	public void testSynchronizeAddSubfileAtATryToMoveToFolderAtB() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File fileFromAAtA = FileTestUtil.createFileRandomContent("subfile2FromA", new Random().nextInt(maxNumChunks) + 1,
				subFolder1A, CHUNK_SIZE);
		logger.info("Upload a new file '{}' at A.", fileFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, fileFromAAtA);

		logger.info("Wait till new file '{}' gets synchronized with B.", fileFromAAtA.getName());
		File fileFromAAtB = new File(subFolder1B, fileFromAAtA.getName());
		waitTillSynchronized(fileFromAAtB);

		File movingFileAtB = new File(sharedFolderB, fileFromAAtA.getName());
		logger.info("Try to move file '{}' at B into shared subfolder '{}'.", fileFromAAtA.getName(), sharedFolderB);
		TestExecutionUtil.executeProcessTillFailed(ProcessFactory.instance().createMoveFileProcess(fileFromAAtB,
				movingFileAtB, nodeB));
		checkIndexAfterTryingToMove(fileFromAAtA, fileFromAAtB);
	}

	@Test
	public void testSynchronizeAddSubfolderAtAMoveToFolderAtA() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File folderFromAAtA = new File(subFolder1A, "subfolder1FromA");
		folderFromAAtA.mkdir();
		logger.info("Upload a new folder '{}' at A.", folderFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, folderFromAAtA);

		logger.info("Wait till new folder '{}' gets synchronized with B.", folderFromAAtA.getName());
		File folderFromAAtB = new File(subFolder1B, folderFromAAtA.getName());
		waitTillSynchronized(folderFromAAtB);

		File movedFolderFromAAtA = new File(sharedFolderA, folderFromAAtA.getName());
		logger.info("Move folder '{}' at A into shared subfolder '{}'.", folderFromAAtA.getName(), sharedFolderA);
		FileUtils.moveDirectory(folderFromAAtA, movedFolderFromAAtA);
		UseCaseTestUtil.moveFile(nodeA, folderFromAAtA, movedFolderFromAAtA);

		logger.info("Wait till new moved folder '{}' gets synchronized with B.", folderFromAAtA.getName());
		File movedFolderFromAAtB = new File(sharedFolderB, folderFromAAtA.getName());
		waitTillSynchronized(movedFolderFromAAtB);
		compareFiles(movedFolderFromAAtA, movedFolderFromAAtB);
		checkIndexAfterMoving(folderFromAAtA, folderFromAAtB, movedFolderFromAAtA, movedFolderFromAAtB);
	}

	@Test
	public void testSynchronizeAddSubfolderAtATryToMoveToFolderAtB() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File folderFromAAtA = new File(subFolder1A, "subfolder2FromA");
		folderFromAAtA.mkdir();
		logger.info("Upload a new folder '{}' at A.", folderFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, folderFromAAtA);

		logger.info("Wait till new folder '{}' gets synchronized with B.", folderFromAAtA.getName());
		File folderFromAAtB = new File(subFolder1B, folderFromAAtA.getName());
		waitTillSynchronized(folderFromAAtB);

		File movingFolderAtB = new File(sharedFolderB, folderFromAAtA.getName());
		logger.info("Try to move folder '{}' at B into shared subfolder '{}'.", folderFromAAtA.getName(), sharedFolderB);
		TestExecutionUtil.executeProcessTillFailed(ProcessFactory.instance().createMoveFileProcess(folderFromAAtB,
				movingFolderAtB, nodeB));
		checkIndexAfterTryingToMove(folderFromAAtA, folderFromAAtB);
	}

	@Test
	public void testSynchronizeAddSubFileAtAMoveToSubfolderAtA() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File fileFromAAtA = FileTestUtil.createFileRandomContent("subfile3FromA", new Random().nextInt(maxNumChunks) + 1,
				subFolder1A, CHUNK_SIZE);
		logger.info("Upload a new file '{}' at A.", fileFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, fileFromAAtA);

		logger.info("Wait till new file '{}' gets synchronized with B.", fileFromAAtA.getName());
		File fileFromAAtB = new File(subFolder1B, fileFromAAtA.getName());
		waitTillSynchronized(fileFromAAtB);

		File movedFileFromAAtA = new File(subFolder2A, fileFromAAtA.getName());
		logger.info("Move file '{}' at A into shared subfolder '{}'.", fileFromAAtA.getName(), subFolder2A);
		FileUtils.moveFile(fileFromAAtA, movedFileFromAAtA);
		UseCaseTestUtil.moveFile(nodeA, fileFromAAtA, movedFileFromAAtA);

		logger.info("Wait till new moved file '{}' gets synchronized with B.", fileFromAAtA.getName());
		File movedFileFromAAtB = new File(subFolder2B, fileFromAAtA.getName());
		waitTillSynchronized(movedFileFromAAtB);
		compareFiles(movedFileFromAAtA, movedFileFromAAtB);
		checkIndexAfterMoving(fileFromAAtA, fileFromAAtB, movedFileFromAAtA, movedFileFromAAtB);
	}

	@Test
	public void testSynchronizeAddSubfileAtATryToMoveToSubfolderAtB() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File fileFromAAtA = FileTestUtil.createFileRandomContent("subfile4FromA", new Random().nextInt(maxNumChunks) + 1,
				subFolder1A, CHUNK_SIZE);
		logger.info("Upload a new file '{}' at A.", fileFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, fileFromAAtA);

		logger.info("Wait till new file '{}' gets synchronized with B.", fileFromAAtA.getName());
		File fileFromAAtB = new File(subFolder1B, fileFromAAtA.getName());
		waitTillSynchronized(fileFromAAtB);

		File movingFileAtB = new File(subFolder2B, fileFromAAtA.getName());
		logger.info("Try to move file '{}' at B into shared subfolder '{}'.", fileFromAAtA.getName(), subFolder2B);
		TestExecutionUtil.executeProcessTillFailed(ProcessFactory.instance().createMoveFileProcess(fileFromAAtB,
				movingFileAtB, nodeB));
		checkIndexAfterTryingToMove(fileFromAAtA, fileFromAAtB);
	}

	@Test
	public void testSynchronizeAddSubfolderAtAMoveToSubfolderAtA() throws NoSessionException, NoPeerConnectionException,
			IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File folderFromAAtA = new File(subFolder1A, "subfolder3FromA");
		folderFromAAtA.mkdir();
		logger.info("Upload a new folder '{}' at A.", folderFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, folderFromAAtA);

		logger.info("Wait till new folder '{}' gets synchronized with B.", folderFromAAtA.getName());
		File folderFromAAtB = new File(subFolder1B, folderFromAAtA.getName());
		waitTillSynchronized(folderFromAAtB);

		File movedFolderFromAAtA = new File(subFolder2A, folderFromAAtA.getName());
		logger.info("Move folder '{}' at A into shared subfolder '{}'.", folderFromAAtA.getName(), subFolder2A);
		FileUtils.moveDirectory(folderFromAAtA, movedFolderFromAAtA);
		UseCaseTestUtil.moveFile(nodeA, folderFromAAtA, movedFolderFromAAtA);

		logger.info("Wait till new moved folder '{}' gets synchronized with B.", folderFromAAtA.getName());
		File movedFolderFromAAtB = new File(subFolder2B, folderFromAAtA.getName());
		waitTillSynchronized(movedFolderFromAAtB);
		compareFiles(movedFolderFromAAtA, movedFolderFromAAtB);
		checkIndexAfterMoving(folderFromAAtA, folderFromAAtB, movedFolderFromAAtA, movedFolderFromAAtB);
	}

	@Test
	public void testSynchronizeAddSubfolderAtATryToMoveToSubfolderAtB() throws NoSessionException,
			NoPeerConnectionException, IOException, IllegalFileLocation, IllegalArgumentException, GetFailedException {
		File folderFromAAtA = new File(subFolder1A, "subfolder4FromA");
		folderFromAAtA.mkdir();
		logger.info("Upload a new folder '{}' at A.", folderFromAAtA.getName());
		UseCaseTestUtil.uploadNewFile(nodeA, folderFromAAtA);

		logger.info("Wait till new folder '{}' gets synchronized with B.", folderFromAAtA.getName());
		File folderFromAAtB = new File(subFolder1B, folderFromAAtA.getName());
		waitTillSynchronized(folderFromAAtB);

		File movingFolderAtB = new File(subFolder2B, folderFromAAtA.getName());
		logger.info("Try to move folder '{}' at B into shared subfolder '{}'.", folderFromAAtA.getName(), subFolder2B);
		TestExecutionUtil.executeProcessTillFailed(ProcessFactory.instance().createMoveFileProcess(folderFromAAtB,
				movingFolderAtB, nodeB));
		checkIndexAfterTryingToMove(folderFromAAtA, folderFromAAtB);
	}

	/**
	 * Waits a certain amount of time till a file appears.
	 * 
	 * @param synchronizingFile
	 *            the file to synchronize
	 */
	private static void waitTillSynchronized(File synchronizingFile) {
		H2HWaiter waiter = new H2HWaiter(40);
		do {
			waiter.tickASecond();
		} while (!synchronizingFile.exists());
	}

	private static void compareFiles(File originalFile, File synchronizedFile) throws IOException {
		Assert.assertEquals(originalFile.getName(), synchronizedFile.getName());
		if (originalFile.isFile() || synchronizedFile.isFile()) {
			Assert.assertTrue(FileUtils.contentEquals(originalFile, synchronizedFile));
		}
	}

	private static void checkIndexAfterMoving(File oldFileA, File oldFileB, File newFileA, File newFileB)
			throws GetFailedException, NoSessionException {
		UserProfile userProfileA = nodeA.getSession().getProfileManager().readUserProfile();
		Index oldIndexAtA = userProfileA.getFileByPath(oldFileA, nodeA.getSession().getRootFile());
		Index newIndexAtA = userProfileA.getFileByPath(newFileA, nodeA.getSession().getRootFile());

		UserProfile userProfileB = nodeB.getSession().getProfileManager().readUserProfile();
		Index oldIndexAtB = userProfileB.getFileByPath(oldFileB, nodeB.getSession().getRootFile());
		Index newIndexAtB = userProfileB.getFileByPath(newFileB, nodeB.getSession().getRootFile());

		// check if old indexes have been removed
		Assert.assertNull(oldIndexAtA);
		Assert.assertNull(oldIndexAtB);

		// check if userA's content protection keys are other ones
		Assert.assertFalse(newIndexAtA.getProtectionKeys().getPrivate()
				.equals(userProfileA.getProtectionKeys().getPrivate()));
		Assert.assertFalse(newIndexAtA.getProtectionKeys().getPublic().equals(userProfileA.getProtectionKeys().getPublic()));
		// check if user B has no content protection keys
		Assert.assertNull(newIndexAtB.getProtectionKeys());

		// check if isShared flag is set
		Assert.assertTrue(newIndexAtA.isShared());
		Assert.assertTrue(newIndexAtB.isShared());

		// check write access
		Assert.assertTrue(newIndexAtA.canWrite());
		// user B has no write access
		Assert.assertFalse(newIndexAtB.canWrite());

		// check user permissions at A
		Set<String> usersA = newIndexAtA.getCalculatedUserList();
		Assert.assertEquals(2, usersA.size());
		Assert.assertTrue(usersA.contains(userA.getUserId()));
		Assert.assertTrue(usersA.contains(userB.getUserId()));

		// check user permissions at A
		Set<String> usersB = newIndexAtB.getCalculatedUserList();
		Assert.assertEquals(2, usersB.size());
		Assert.assertTrue(usersB.contains(userA.getUserId()));
		Assert.assertTrue(usersB.contains(userB.getUserId()));

		// check user permissions in case of a folder at A
		if (newIndexAtA.isFolder()) {
			Set<UserPermission> permissions = ((FolderIndex) newIndexAtA).getCalculatedUserPermissions();
			Assert.assertEquals(2, permissions.size());
			Assert.assertTrue(permissions.contains(new UserPermission(userA.getUserId(), PermissionType.WRITE)));
			Assert.assertTrue(permissions.contains(new UserPermission(userB.getUserId(), PermissionType.READ)));
		}

		// check user permissions in case of a folder at B
		if (newIndexAtB.isFolder()) {
			Set<UserPermission> permissions = ((FolderIndex) newIndexAtB).getCalculatedUserPermissions();
			Assert.assertEquals(2, permissions.size());
			Assert.assertTrue(permissions.contains(new UserPermission(userA.getUserId(), PermissionType.WRITE)));
			Assert.assertTrue(permissions.contains(new UserPermission(userB.getUserId(), PermissionType.READ)));
		}
	}

	private static void checkIndexAfterTryingToMove(File fileA, File fileB) throws GetFailedException, NoSessionException {
		UserProfile userProfileA = nodeA.getSession().getProfileManager().readUserProfile();
		Index indexAtA = userProfileA.getFileByPath(fileA, nodeA.getSession().getRootFile());

		UserProfile userProfileB = nodeB.getSession().getProfileManager().readUserProfile();
		Index indexAtB = userProfileB.getFileByPath(fileB, nodeB.getSession().getRootFile());

		// check if userA's content protection keys are other ones
		Assert.assertFalse(indexAtA.getProtectionKeys().getPrivate().equals(userProfileA.getProtectionKeys().getPrivate()));
		Assert.assertFalse(indexAtA.getProtectionKeys().getPublic().equals(userProfileA.getProtectionKeys().getPublic()));
		// check if user B has no content protection keys
		Assert.assertNull(indexAtB.getProtectionKeys());

		// check if isShared flag is set
		Assert.assertTrue(indexAtA.isShared());
		Assert.assertTrue(indexAtB.isShared());

		// check write access
		Assert.assertTrue(indexAtA.canWrite());
		// user B has no write access
		Assert.assertFalse(indexAtB.canWrite());

		// check user permissions at A
		Set<String> usersA = indexAtA.getCalculatedUserList();
		Assert.assertEquals(2, usersA.size());
		Assert.assertTrue(usersA.contains(userA.getUserId()));
		Assert.assertTrue(usersA.contains(userB.getUserId()));

		// check user permissions at A
		Set<String> usersB = indexAtB.getCalculatedUserList();
		Assert.assertEquals(2, usersB.size());
		Assert.assertTrue(usersB.contains(userA.getUserId()));
		Assert.assertTrue(usersB.contains(userB.getUserId()));

		// check user permissions in case of a folder at A
		if (indexAtA.isFolder()) {
			Set<UserPermission> permissions = ((FolderIndex) indexAtA).getCalculatedUserPermissions();
			Assert.assertEquals(2, permissions.size());
			Assert.assertTrue(permissions.contains(new UserPermission(userA.getUserId(), PermissionType.WRITE)));
			Assert.assertTrue(permissions.contains(new UserPermission(userB.getUserId(), PermissionType.READ)));
		}

		// check user permissions in case of a folder at B
		if (indexAtB.isFolder()) {
			Set<UserPermission> permissions = ((FolderIndex) indexAtB).getCalculatedUserPermissions();
			Assert.assertEquals(2, permissions.size());
			Assert.assertTrue(permissions.contains(new UserPermission(userA.getUserId(), PermissionType.WRITE)));
			Assert.assertTrue(permissions.contains(new UserPermission(userB.getUserId(), PermissionType.READ)));
		}
	}

	@AfterClass
	public static void endTest() throws IOException {
		FileUtils.deleteDirectory(rootA);
		FileUtils.deleteDirectory(rootB);

		NetworkTestUtil.shutdownNetwork(network);
		afterClass();
	}

}