/*
 *  Copyright 2017-2022 Adobe.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.adobe.testing.s3mock.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.rangeClosed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Files.contentOf;

import com.adobe.testing.s3mock.dto.Bucket;
import com.adobe.testing.s3mock.dto.MultipartUpload;
import com.adobe.testing.s3mock.dto.Owner;
import com.adobe.testing.s3mock.dto.Part;
import com.adobe.testing.s3mock.dto.Range;
import com.adobe.testing.s3mock.dto.Tag;
import com.adobe.testing.s3mock.util.DigestUtil;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@SpringBootTest(classes = {DomainConfiguration.class})
class FileStoreTest {

  private static final String SIGNED_CONTENT =
      "24;chunk-signature=11707b33deb094881a16c70e9cbd5d79053a0bb235c25674e3cf0fed601683b5\r\n"
          + "## sample test file ##\n"
          + "\n"
          + "demo=content\n"
          + "0;chunk-signature=2206490f19c068b46367173d1e155b597fd367037fa3f924290b41c1e83c1c08";

  private static final String UNSIGNED_CONTENT =
      "## sample test file ##\n"
          + "\n"
          + "demo=content";

  private static final String TEST_BUCKET_NAME = "testbucket";
  private static final String ALL_BUCKETS = null;

  private static final String TEST_FILE_PATH = "src/test/resources/sampleFile.txt";

  private static final String TEST_ENC_TYPE = "aws:kms";

  private static final String TEST_ENC_KEY = "aws:kms" + UUID.randomUUID();

  private static final String DEFAULT_CONTENT_TYPE =
      ContentType.APPLICATION_OCTET_STREAM.toString();

  private static final Owner TEST_OWNER = new Owner(123, "s3-mock-file-store");

  private static final String TEXT_PLAIN = ContentType.TEXT_PLAIN.toString();
  private static final String ENCODING_GZIP = "gzip";

  @Autowired
  private FileStore fileStore;

  @MockBean
  private KmsKeyStore kmsKeyStore;

  /**
   * Creates a bucket and checks that it exists.
   *
   */
  @Test
  void shouldCreateBucket() {
    final Bucket bucket = fileStore.createBucket(TEST_BUCKET_NAME);
    assertThat(bucket.getName()).as("Bucket should have been created.").endsWith(TEST_BUCKET_NAME);
  }

  /**
   * Checks if Bucket exists.
   *
   */
  @Test
  void bucketShouldExist() {
    fileStore.createBucket(TEST_BUCKET_NAME);

    final Boolean doesBucketExist = fileStore.doesBucketExist(TEST_BUCKET_NAME);

    assertThat(doesBucketExist).as(
            String.format("The previously created bucket, '%s', should exist!", TEST_BUCKET_NAME))
        .isTrue();
  }

  /**
   * Checks if bucket doesn't exist.
   */
  @Test
  void bucketShouldNotExist() {
    final Boolean doesBucketExist = fileStore.doesBucketExist(TEST_BUCKET_NAME);

    assertThat(doesBucketExist).as(
        String.format("The bucket, '%s', should not exist!", TEST_BUCKET_NAME)).isFalse();
  }

  /**
   * Checks if created buckets are listed.
   *
   */
  @Test
  void shouldHoldAllBuckets() {
    final String bucketName1 = "myNüwNämeÄins";
    final String bucketName2 = "myNüwNämeZwöei";
    final String bucketName3 = "myNüwNämeDrü";

    fileStore.createBucket(bucketName1);
    fileStore.createBucket(bucketName2);
    fileStore.createBucket(bucketName3);

    final List<Bucket> buckets = fileStore.listBuckets();

    assertThat(buckets.size()).as("FileStore should hold three Buckets").isEqualTo(3);
  }

  /**
   * Creates a bucket an checks that it can be retrieved by it's name.
   *
   */
  @Test
  void shouldGetBucketByName() {
    fileStore.createBucket(TEST_BUCKET_NAME);
    final Bucket bucket = fileStore.getBucket(TEST_BUCKET_NAME);

    assertThat(bucket).as("Bucket should not be null").isNotNull();
    assertThat(bucket.getName()).as("Bucket name should end with " + TEST_BUCKET_NAME)
        .isEqualTo(TEST_BUCKET_NAME);
  }

  /**
   * Checks that an object can be stored in a bucket.
   *
   * @throws Exception If an Exception occurred.
   */
  @Test
  void shouldStoreFileInBucket() throws Exception {
    final File sourceFile = new File(TEST_FILE_PATH);
    final String name = sourceFile.getName();
    final String md5 = DigestUtil.getHexDigest(new FileInputStream(sourceFile));
    final String size = Long.toString(sourceFile.length());

    final S3Object returnedObject =
        fileStore.putS3Object(TEST_BUCKET_NAME, name, null, ENCODING_GZIP,
            new FileInputStream(sourceFile), false);

    assertThat(returnedObject.getName()).as("Name should be '" + name + "'").isEqualTo(name);
    assertThat(returnedObject.getContentType()).as(
        "ContentType should be '" + "binary/octet-stream" + "'").isEqualTo("binary/octet-stream");
    assertThat(returnedObject.getContentEncoding()).as(
        "ContentEncoding should be '" + ENCODING_GZIP + "'").isEqualTo(ENCODING_GZIP);
    assertThat(returnedObject.getEtag()).as("MD5 should be '" + md5 + "'").isEqualTo(md5);
    assertThat(returnedObject.getSize()).as("Size should be '" + size + "'").isEqualTo(size);
    assertThat(returnedObject.isEncrypted()).as("File should not be encrypted!").isFalse();

    assertThat(contentOf(sourceFile, UTF_8)).as("Files should be equal").isEqualTo(
        contentOf(returnedObject.getDataFile(), UTF_8));
  }

  /**
   * Checks that an object can be stored in a bucket.
   *
   * @throws Exception If an Exception occurred.
   */
  @Test
  void shouldStoreObjectEncrypted() throws Exception {
    final File sourceFile = new File(TEST_FILE_PATH);

    final String name = sourceFile.getName();
    final String contentType = ContentType.TEXT_PLAIN.toString();
    final String md5 = DigestUtil.getHexDigest(TEST_ENC_KEY,
        new ByteArrayInputStream(UNSIGNED_CONTENT.getBytes(UTF_8)));

    final S3Object storedObject =
        fileStore.putS3Object(TEST_BUCKET_NAME,
            name,
            contentType,
            null,
            new ByteArrayInputStream(SIGNED_CONTENT.getBytes(UTF_8)),
            true,
            Collections.emptyMap(),
            TEST_ENC_TYPE,
            TEST_ENC_KEY);

    assertThat(storedObject.getSize()).as("Filelength matches").isEqualTo("36");
    assertThat(storedObject.isEncrypted()).as("File should be encrypted").isTrue();
    assertThat(storedObject.getKmsEncryption()).as("Encryption Type matches")
        .isEqualTo(TEST_ENC_TYPE);
    assertThat(storedObject.getKmsKeyId()).as("Encryption Key matches").isEqualTo(TEST_ENC_KEY);
    assertThat(storedObject.getEtag()).as("MD5 should not match").isEqualTo(md5);
  }

  /**
   * Checks that an object can be stored in a bucket.
   *
   * @throws Exception If an Exception occurred.
   */
  @Test
  void shouldGetEncryptedObject() throws Exception {
    final File sourceFile = new File(TEST_FILE_PATH);

    final String name = sourceFile.getName();
    final String contentType = ContentType.TEXT_PLAIN.toString();
    final String md5 = DigestUtil.getHexDigest(TEST_ENC_KEY,
        new ByteArrayInputStream(UNSIGNED_CONTENT.getBytes(UTF_8)));

    fileStore.putS3Object(TEST_BUCKET_NAME,
        name,
        contentType,
        null,
        new ByteArrayInputStream(SIGNED_CONTENT.getBytes(UTF_8)),
        true,
        Collections.emptyMap(),
        TEST_ENC_TYPE,
        TEST_ENC_KEY);

    final S3Object returnedObject = fileStore.getS3Object(TEST_BUCKET_NAME, name);
    assertThat(returnedObject.getSize()).as("Filelength matches").isEqualTo("36");
    assertThat(returnedObject.isEncrypted()).as("File should be encrypted").isTrue();
    assertThat(returnedObject.getKmsEncryption()).as("Encryption Type matches")
        .isEqualTo(TEST_ENC_TYPE);
    assertThat(returnedObject.getKmsKeyId()).as("Encryption Key matches").isEqualTo(TEST_ENC_KEY);
    assertThat(returnedObject.getEtag()).as("MD5 should not match").isEqualTo(md5);
  }

  /**
   * Checks that a previously created object can be retrieved from a bucket.
   *
   * @throws Exception if an Exception occurred.
   */
  @Test
  void shouldGetFile() throws Exception {
    final File sourceFile = new File(TEST_FILE_PATH);

    final String name = sourceFile.getName();
    final String md5 = DigestUtil.getHexDigest(new FileInputStream(sourceFile));
    final String size = Long.toString(sourceFile.length());

    fileStore
        .putS3Object(TEST_BUCKET_NAME, name, TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(sourceFile), false);

    final S3Object returnedObject = fileStore.getS3Object(TEST_BUCKET_NAME, name);

    assertThat(returnedObject.getName()).as("Name should be '" + name + "'").isEqualTo(name);
    assertThat(returnedObject.getContentType()).as(
        "ContentType should be '" + TEXT_PLAIN + "'").isEqualTo(TEXT_PLAIN);
    assertThat(returnedObject.getContentEncoding()).as(
        "ContentEncoding should be '" + ENCODING_GZIP + "'").isEqualTo(ENCODING_GZIP);
    assertThat(returnedObject.getEtag()).as("MD5 should be '" + md5 + "'").isEqualTo(md5);
    assertThat(returnedObject.getSize()).as("Size should be '" + size + "'").isEqualTo(size);
    assertThat(returnedObject.isEncrypted()).as("File should not be encrypted!").isFalse();

    assertThat(contentOf(sourceFile, UTF_8)).as("Files should be equal").isEqualTo(
        contentOf(returnedObject.getDataFile(), UTF_8));
  }

  /**
   * Checks that a previously created object can be retrieved from a bucket.
   *
   * @throws Exception if an Exception occurred.
   */
  @Test
  void shouldGetFileWithSlashAtStart() throws Exception {
    final File sourceFile = new File(TEST_FILE_PATH);

    final String name = "/app/config/" + sourceFile.getName();
    final String md5 = DigestUtil.getHexDigest(new FileInputStream(sourceFile));
    final String size = Long.toString(sourceFile.length());

    fileStore
        .putS3Object(TEST_BUCKET_NAME, name, TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(sourceFile), false);

    final S3Object returnedObject = fileStore.getS3Object(TEST_BUCKET_NAME, name);

    assertThat(returnedObject.getName()).as("Name should be '" + name + "'").isEqualTo(name);
    assertThat(returnedObject.getContentType()).as(
        "ContentType should be '" + TEXT_PLAIN + "'").isEqualTo(TEXT_PLAIN);
    assertThat(returnedObject.getContentEncoding()).as(
        "ContentEncoding should be '" + ENCODING_GZIP + "'").isEqualTo(ENCODING_GZIP);
    assertThat(returnedObject.getEtag()).as("MD5 should be '" + md5 + "'").isEqualTo(md5);
    assertThat(returnedObject.getSize()).as("Size should be '" + size + "'").isEqualTo(size);
    assertThat(returnedObject.isEncrypted()).as("File should not be encrypted!").isFalse();

    assertThat(contentOf(sourceFile, UTF_8)).as("Files should be equal").isEqualTo(
        contentOf(returnedObject.getDataFile(), UTF_8));
  }

  /**
   * Checks that we can set and retrieve tags for a given file.
   *
   * @throws Exception if an Exception occurred.
   */
  @Test
  void shouldSetAndGetTags() throws Exception {
    final File sourceFile = new File(TEST_FILE_PATH);

    final String name = sourceFile.getName();

    fileStore.putS3Object(TEST_BUCKET_NAME, name, TEXT_PLAIN, ENCODING_GZIP,
        new FileInputStream(sourceFile), false);

    final List<Tag> tags = new ArrayList<>();
    tags.add(new Tag("foo", "bar"));
    fileStore.setObjectTags(TEST_BUCKET_NAME, name, tags);

    final S3Object returnedObject = fileStore.getS3Object(TEST_BUCKET_NAME, name);

    assertThat(returnedObject.getTags().get(0).getKey()).as("Tag should be present")
        .isEqualTo("foo");
    assertThat(returnedObject.getTags().get(0).getValue()).as("Tag value should be bar")
        .isEqualTo("bar");
  }

  /**
   * Tests if an object can be copied from one to another bucket.
   *
   * @throws Exception if files can't be read.
   */
  @Test
  void shouldCopyObject() throws Exception {
    final String destinationObjectName = "destinationObject";
    final String destinationBucketName = "destinationBucket";

    final File sourceFile = new File(TEST_FILE_PATH);

    final String sourceBucketName = "sourceBucket";
    final String sourceObjectName = sourceFile.getName();

    fileStore.putS3Object(sourceBucketName, sourceObjectName, TEXT_PLAIN, ENCODING_GZIP,
        new FileInputStream(sourceFile), false);

    fileStore.copyS3Object(sourceBucketName, sourceObjectName, destinationBucketName,
        destinationObjectName);
    final S3Object copiedObject =
        fileStore.getS3Object(destinationBucketName, destinationObjectName);

    assertThat(copiedObject.isEncrypted()).as("File should not be encrypted!").isFalse();
    assertThat(contentOf(sourceFile, UTF_8)).as("Files should be equal!").isEqualTo(
        contentOf(copiedObject.getDataFile(), UTF_8));
  }

  /**
   * Tests if an object can be copied from one to another bucket.
   *
   * @throws Exception if files can't be read.
   */
  @Test
  void shouldCopyObjectEncrypted() throws Exception {
    final String destinationObjectName = "destinationObject";
    final String destinationBucketName = "destinationBucket";

    final File sourceFile = new File(TEST_FILE_PATH);

    final String sourceBucketName = "sourceBucket";
    final String sourceObjectName = sourceFile.getName();
    final String md5 = DigestUtil.getHexDigest(TEST_ENC_KEY, new FileInputStream(sourceFile));

    fileStore.putS3Object(sourceBucketName, sourceObjectName, TEXT_PLAIN, ENCODING_GZIP,
        new FileInputStream(sourceFile), false);

    fileStore.copyS3ObjectEncrypted(sourceBucketName,
        sourceObjectName,
        destinationBucketName,
        destinationObjectName,
        TEST_ENC_TYPE,
        TEST_ENC_KEY);

    final S3Object copiedObject =
        fileStore.getS3Object(destinationBucketName, destinationObjectName);

    assertThat(copiedObject.isEncrypted()).as("File should be encrypted!").isTrue();
    assertThat(copiedObject.getSize()).as("Files should have the same length").isEqualTo(
        String.valueOf(sourceFile.length()));
    assertThat(copiedObject.getEtag()).as("MD5 should match").isEqualTo(md5);
  }

  /**
   * Tests if an object can be deleted.
   *
   * @throws Exception if an FileNotFoundException or IOException is thrown
   */
  @Test
  void shouldDeleteObject() throws Exception {
    final File sourceFile = new File(TEST_FILE_PATH);

    final String objectName = sourceFile.getName();

    fileStore
        .putS3Object(TEST_BUCKET_NAME, objectName, TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(sourceFile), false);
    final boolean objectDeleted = fileStore.deleteObject(TEST_BUCKET_NAME, objectName);
    final S3Object s3Object = fileStore.getS3Object(TEST_BUCKET_NAME, objectName);

    assertThat(objectDeleted).as("Deletion should succeed!").isTrue();
    assertThat(s3Object).as("Object should be null!").isNull();
  }

  /**
   * Checks if a bucket can be deleted.
   *
   * @throws Exception if an Exception occurred.
   */
  @Test
  void shouldDeleteBucket() throws Exception {
    final File sourceFile = new File(TEST_FILE_PATH);
    final String objectName = sourceFile.getName();

    fileStore.createBucket(TEST_BUCKET_NAME);
    fileStore
        .putS3Object(TEST_BUCKET_NAME, objectName, TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(sourceFile), false);

    final boolean bucketDeleted = fileStore.deleteBucket(TEST_BUCKET_NAME);

    final Bucket bucket = fileStore.getBucket(TEST_BUCKET_NAME);

    assertThat(bucketDeleted).as("Deletion should succeed!").isTrue();
    assertThat(bucket).as("Bucket should be null!").isNull();
  }

  @Test
  void shouldCreateMultipartUploadFolder() {
    String fileName = "aFile";
    String uploadId = "12345";
    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, fileName, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP,
        uploadId, TEST_OWNER, TEST_OWNER);

    final File destinationFolder =
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, fileName, uploadId)
            .toFile();

    assertThat(destinationFolder.exists()).as("Destination folder does not exist").isTrue();
    assertThat(destinationFolder.isDirectory()).as("Destination folder is not a directory")
        .isTrue();

    fileStore.abortMultipartUpload(TEST_BUCKET_NAME, fileName, uploadId);
  }

  @Test
  void shouldCreateMultipartUploadFolderIfBucketExists() {
    fileStore.createBucket(TEST_BUCKET_NAME);
    String uploadId = "12345";
    String fileName = "aFile";
    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, fileName, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP, uploadId, TEST_OWNER, TEST_OWNER);

    final File destinationFolder =
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, fileName, uploadId)
            .toFile();

    assertThat(destinationFolder.exists()).as("Destination folder does not exist").isTrue();
    assertThat(destinationFolder.isDirectory()).as("Destination folder is not a directory")
        .isTrue();

    fileStore.abortMultipartUpload(TEST_BUCKET_NAME, fileName, uploadId);
  }

  @Test
  void shouldStorePart() throws Exception {

    final String fileName = "PartFile";
    final String uploadId = "12345";
    final String partNumber = "1";
    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, fileName, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP, uploadId, TEST_OWNER, TEST_OWNER);

    fileStore.putPart(
        TEST_BUCKET_NAME, fileName, uploadId, partNumber,
        new ByteArrayInputStream("Test".getBytes()), false);

    assertThat(
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, fileName, uploadId,
                partNumber + ".part")
            .toFile()
            .exists()).as("Part does not exist!").isTrue();

    fileStore.abortMultipartUpload(TEST_BUCKET_NAME, fileName, uploadId);
  }

  @Test
  void shouldFinishUpload() throws Exception {
    final String fileName = "PartFile";
    final String uploadId = "12345";
    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, fileName, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP, uploadId, TEST_OWNER, TEST_OWNER);
    fileStore
        .putPart(TEST_BUCKET_NAME, fileName, uploadId, "1",
            new ByteArrayInputStream("Part1".getBytes()), false);
    fileStore
        .putPart(TEST_BUCKET_NAME, fileName, uploadId, "2",
            new ByteArrayInputStream("Part2".getBytes()), false);

    final String etag =
        fileStore.completeMultipartUpload(TEST_BUCKET_NAME, fileName, uploadId, getParts(2));
    final byte[] allMd5s = ArrayUtils.addAll(
        DigestUtils.md5("Part1"),
        DigestUtils.md5("Part2")
    );

    assertThat(
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, fileName,
                "fileData").toFile()
            .exists()).as("File does not exist!").isTrue();
    assertThat(
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, fileName,
                "metadata").toFile()
            .exists()).as("Metadata does not exist!").isTrue();
    assertThat(DigestUtils.md5Hex(allMd5s) + "-2").as("Special etag doesn't match.")
        .isEqualTo(etag);
  }

  @Test
  void hasValidMetadata() throws Exception {
    final String fileName = "PartFile";
    final String uploadId = "12345";
    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, fileName, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP, uploadId, TEST_OWNER, TEST_OWNER);
    fileStore
        .putPart(TEST_BUCKET_NAME, fileName, uploadId, "1",
            new ByteArrayInputStream("Part1".getBytes()), false);
    fileStore
        .putPart(TEST_BUCKET_NAME, fileName, uploadId, "2",
            new ByteArrayInputStream("Part2".getBytes()), false);

    fileStore.completeMultipartUpload(TEST_BUCKET_NAME, fileName, uploadId, getParts(2));

    final S3Object s3Object = fileStore.getS3Object(TEST_BUCKET_NAME, "PartFile");
    assertThat(s3Object.getSize()).as("Size doesn't match.").isEqualTo("10");
    assertThat(s3Object.getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM.toString());
  }

  private List<Part> getParts(int n) {
    List<Part> parts = new ArrayList<>();
    for (int i = 1; i <= n; i++) {
      Part part = new Part();
      part.setPartNumber(i);
      parts.add(part);
    }
    return parts;
  }

  @Test
  void returnsValidPartsFromMultipart() throws IOException {
    final String fileName = "PartFile";
    final String uploadId = "12345";
    String part1 = "Part1";
    ByteArrayInputStream part1Stream = new ByteArrayInputStream(part1.getBytes());
    String part2 = "Part2";
    ByteArrayInputStream part2Stream = new ByteArrayInputStream(part2.getBytes());

    final Part expectedPart1 = prepareExpectedPart(1, part1);
    final Part expectedPart2 = prepareExpectedPart(2, part2);

    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, fileName, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP, uploadId, TEST_OWNER, TEST_OWNER);

    fileStore.putPart(TEST_BUCKET_NAME, fileName, uploadId, "1", part1Stream, false);
    fileStore.putPart(TEST_BUCKET_NAME, fileName, uploadId, "2", part2Stream, false);

    List<Part> parts = fileStore.getMultipartUploadParts(TEST_BUCKET_NAME, fileName, uploadId);

    assertThat(parts.size()).as("Part quantity does not match").isEqualTo(2);

    expectedPart1.setLastModified(parts.get(0).getLastModified());
    expectedPart2.setLastModified(parts.get(1).getLastModified());

    assertThat(parts.get(0)).as("Part 1 attributes doesn't match").isEqualTo(expectedPart1);
    assertThat(parts.get(1)).as("Part 2 attributes doesn't match").isEqualTo(expectedPart2);

    fileStore.abortMultipartUpload(TEST_BUCKET_NAME, fileName, uploadId);
  }

  private Part prepareExpectedPart(final int partNumber, final String content) {
    Part part = new Part();
    part.setETag(String.format("%s", DigestUtils.md5Hex(content)));
    part.setPartNumber(partNumber);
    part.setSize((long) content.getBytes().length);
    return part;
  }

  @Test
  void deletesTemporaryMultipartUploadFolder() throws Exception {
    final String fileName = "PartFile";
    final String uploadId = "12345";
    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, fileName, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP, uploadId, TEST_OWNER, TEST_OWNER);
    fileStore
        .putPart(TEST_BUCKET_NAME, fileName, uploadId, "1",
            new ByteArrayInputStream("Part1".getBytes()), false);

    fileStore.completeMultipartUpload(TEST_BUCKET_NAME, fileName, uploadId, getParts(1));

    assertThat(
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, fileName, uploadId)
            .toFile()
            .exists()).as("Folder should not exist anymore!").isFalse();
  }

  @Test
  void listsMultipartUploads() {
    assertThat(fileStore.listMultipartUploads(ALL_BUCKETS)).isEmpty();

    final String fileName = "PartFile";
    final String uploadId = "12345";
    final MultipartUpload initiatedUpload = fileStore
        .prepareMultipartUpload(TEST_BUCKET_NAME, fileName, DEFAULT_CONTENT_TYPE, ENCODING_GZIP,
            uploadId, TEST_OWNER, TEST_OWNER);

    final Collection<MultipartUpload> uploads = fileStore.listMultipartUploads(TEST_BUCKET_NAME);
    assertThat(uploads).hasSize(1);
    final MultipartUpload upload = uploads.iterator().next();
    assertThat(upload).isEqualTo(initiatedUpload);
    // and some specific sanity checks
    assertThat(upload.getUploadId()).isEqualTo(uploadId);
    assertThat(upload.getKey()).isEqualTo(fileName);

    fileStore.completeMultipartUpload(TEST_BUCKET_NAME, fileName, uploadId, getParts(0));

    assertThat(fileStore.listMultipartUploads(ALL_BUCKETS)).isEmpty();
  }

  @Test
  void listsMultipartUploadsMultipleBuckets() {
    assertThat(fileStore.listMultipartUploads(ALL_BUCKETS)).isEmpty();

    final String fileName1 = "PartFile1";
    final String uploadId1 = "123451";
    final String bucketName1 = "bucket1";
    final MultipartUpload initiatedUpload1 = fileStore
        .prepareMultipartUpload(bucketName1, fileName1, DEFAULT_CONTENT_TYPE, ENCODING_GZIP,
            uploadId1, TEST_OWNER, TEST_OWNER);
    final String fileName2 = "PartFile2";
    final String uploadId2 = "123452";
    final String bucketName2 = "bucket2";
    final MultipartUpload initiatedUpload2 = fileStore
        .prepareMultipartUpload(bucketName2, fileName2, DEFAULT_CONTENT_TYPE, ENCODING_GZIP,
            uploadId2, TEST_OWNER, TEST_OWNER);

    final Collection<MultipartUpload> uploads1 = fileStore.listMultipartUploads(bucketName1);
    assertThat(uploads1).hasSize(1);
    final MultipartUpload upload1 = uploads1.iterator().next();
    assertThat(upload1).isEqualTo(initiatedUpload1);
    // and some specific sanity checks
    assertThat(upload1.getUploadId()).isEqualTo(uploadId1);
    assertThat(upload1.getKey()).isEqualTo(fileName1);

    final Collection<MultipartUpload> uploads2 = fileStore.listMultipartUploads(bucketName2);
    assertThat(uploads2).hasSize(1);
    final MultipartUpload upload2 = uploads2.iterator().next();
    assertThat(upload2).isEqualTo(initiatedUpload2);
    // and some specific sanity checks
    assertThat(upload2.getUploadId()).isEqualTo(uploadId2);
    assertThat(upload2.getKey()).isEqualTo(fileName2);

    fileStore.completeMultipartUpload(bucketName1, fileName1, uploadId1, getParts(0));
    fileStore.completeMultipartUpload(bucketName2, fileName2, uploadId2, getParts(0));

    assertThat(fileStore.listMultipartUploads(ALL_BUCKETS)).isEmpty();
  }

  @Test
  void abortMultipartUpload() throws Exception {
    assertThat(fileStore.listMultipartUploads(ALL_BUCKETS)).isEmpty();

    final String fileName = "PartFile";
    final String uploadId = "12345";
    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, fileName, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP, uploadId, TEST_OWNER, TEST_OWNER);
    fileStore.putPart(TEST_BUCKET_NAME, fileName, uploadId, "1",
        new ByteArrayInputStream("Part1".getBytes()), false);
    assertThat(fileStore.listMultipartUploads(TEST_BUCKET_NAME)).hasSize(1);

    fileStore.abortMultipartUpload(TEST_BUCKET_NAME, fileName, uploadId);

    assertThat(fileStore.listMultipartUploads(ALL_BUCKETS)).isEmpty();
    assertThat(
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, fileName,
                "fileData").toFile()
            .exists()).as("File exists!").isFalse();
    assertThat(
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, fileName,
                "metadata").toFile()
            .exists()).as("Metadata exists!").isFalse();
    assertThat(
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, fileName, uploadId)
            .toFile()
            .exists()).as("Temp upload folder exists!").isFalse();
  }

  @Test
  void copyPart() throws Exception {

    final String sourceFile = UUID.randomUUID().toString();
    final String uploadId = UUID.randomUUID().toString();

    final String targetFile = UUID.randomUUID().toString();
    final String partNumber = "1";

    final byte[] contentBytes = UUID.randomUUID().toString().getBytes();
    fileStore.putS3Object(TEST_BUCKET_NAME, sourceFile, DEFAULT_CONTENT_TYPE, ENCODING_GZIP,
        new ByteArrayInputStream(contentBytes), false);

    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, targetFile, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP, uploadId, TEST_OWNER, TEST_OWNER);

    Range range = new Range(0, contentBytes.length);
    fileStore.copyPart(
        TEST_BUCKET_NAME, sourceFile, range, partNumber,
        TEST_BUCKET_NAME, targetFile, uploadId);

    assertThat(
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, targetFile,
                uploadId,
                partNumber + ".part")
            .toFile()
            .exists()).as("Part does not exist!").isTrue();
    fileStore.abortMultipartUpload(TEST_BUCKET_NAME, targetFile, uploadId);
  }

  @Test
  void copyPartNoRange() throws Exception {

    final String sourceFile = UUID.randomUUID().toString();
    final String uploadId = UUID.randomUUID().toString();

    final String targetFile = UUID.randomUUID().toString();
    final String partNumber = "1";

    final byte[] contentBytes = UUID.randomUUID().toString().getBytes();
    fileStore.putS3Object(TEST_BUCKET_NAME, sourceFile, DEFAULT_CONTENT_TYPE, ENCODING_GZIP,
        new ByteArrayInputStream(contentBytes), false);

    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, targetFile, DEFAULT_CONTENT_TYPE,
        ENCODING_GZIP, uploadId, TEST_OWNER, TEST_OWNER);

    Range range = null;
    fileStore.copyPart(
        TEST_BUCKET_NAME, sourceFile, range, partNumber,
        TEST_BUCKET_NAME, targetFile, uploadId);

    assertThat(
        Paths.get(fileStore.getRootFolder().getAbsolutePath(), TEST_BUCKET_NAME, targetFile,
                uploadId,
                partNumber + ".part")
            .toFile()
            .exists()).as("Part does not exist!").isTrue();
    fileStore.abortMultipartUpload(TEST_BUCKET_NAME, targetFile, uploadId);
  }

  @Test
  void missingUploadPreparation() {
    Range range = new Range(0, 0);
    IllegalStateException e = Assertions.assertThrows(IllegalStateException.class, () ->
        fileStore.copyPart(
            TEST_BUCKET_NAME, UUID.randomUUID().toString(), range, "1",
            TEST_BUCKET_NAME, UUID.randomUUID().toString(), UUID.randomUUID().toString())
    );

    assertThat(e.getMessage()).isEqualTo("Missed preparing Multipart Request");
  }

  @Test
  void getObject() throws Exception {
    fileStore.createBucket(TEST_BUCKET_NAME);
    fileStore
        .putS3Object(TEST_BUCKET_NAME, "a/b/c", TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(TEST_FILE_PATH),
            false);
    final List<S3Object> result = fileStore.getS3Objects(TEST_BUCKET_NAME, "a/b/c");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("a/b/c");
  }

  @Test
  void getObjectsForParentDirectory() throws Exception {
    fileStore.createBucket(TEST_BUCKET_NAME);
    fileStore
        .putS3Object(TEST_BUCKET_NAME, "a/b/c", TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(TEST_FILE_PATH),
            false);
    final List<S3Object> result = fileStore.getS3Objects(TEST_BUCKET_NAME, "a/b");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("a/b/c");
  }

  @Test
  void getObjectsForPartialPrefix() throws Exception {
    fileStore.createBucket(TEST_BUCKET_NAME);
    fileStore
        .putS3Object(TEST_BUCKET_NAME, "foo_bar_baz", TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(TEST_FILE_PATH),
            false);
    final List<S3Object> result = fileStore.getS3Objects(TEST_BUCKET_NAME, "fo");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("foo_bar_baz");
  }

  @Test
  void getObjectsForEmptyPrefix() throws Exception {
    fileStore.createBucket(TEST_BUCKET_NAME);
    fileStore
        .putS3Object(TEST_BUCKET_NAME, "a", TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(TEST_FILE_PATH),
            false);
    final List<S3Object> result = fileStore.getS3Objects(TEST_BUCKET_NAME, "");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("a");
  }

  @Test
  void getObjectsForNullPrefix() throws Exception {
    fileStore.createBucket(TEST_BUCKET_NAME);
    fileStore
        .putS3Object(TEST_BUCKET_NAME, "a", TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(TEST_FILE_PATH),
            false);
    final List<S3Object> result = fileStore.getS3Objects(TEST_BUCKET_NAME, null);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("a");
  }

  @Test
  void getObjectsForPartialParentDirectory() throws Exception {
    fileStore.createBucket(TEST_BUCKET_NAME);
    fileStore
        .putS3Object(TEST_BUCKET_NAME, "a/bee/c", TEXT_PLAIN, ENCODING_GZIP,
            new FileInputStream(TEST_FILE_PATH),
            false);
    final List<S3Object> result = fileStore.getS3Objects(TEST_BUCKET_NAME, "a/b");
    assertThat(result).hasSize(1);
  }

  @Test
  void multipartUploadPartsAreSortedNumerically() throws IOException {
    fileStore.createBucket(TEST_BUCKET_NAME);

    final String uploadId = UUID.randomUUID().toString();
    final String filename = UUID.randomUUID().toString();

    fileStore.prepareMultipartUpload(TEST_BUCKET_NAME, filename, TEXT_PLAIN, ENCODING_GZIP,
        uploadId, TEST_OWNER, TEST_OWNER);
    for (int i = 1; i < 11; i++) {
      final ByteArrayInputStream inputStream = new ByteArrayInputStream((i + "\n").getBytes());

      fileStore.putPart(TEST_BUCKET_NAME, filename, uploadId, String.valueOf(i),
          inputStream, false);
    }
    fileStore.completeMultipartUpload(TEST_BUCKET_NAME, filename, uploadId, getParts(10));
    final List<String> s = FileUtils
        .readLines(fileStore.getS3Object(TEST_BUCKET_NAME, filename).getDataFile(),
            "UTF8");

    assertThat(s).contains(rangeClosed(1, 10).mapToObj(Integer::toString)
        .collect(toList()).toArray(new String[] {}));
  }

  /**
   * Deletes all existing buckets.
   *
   * @throws Exception if bucket could not be deleted.
   */
  @AfterEach
  void cleanupFilestore() throws Exception {
    for (final Bucket bucket : fileStore.listBuckets()) {
      fileStore.deleteBucket(bucket.getName());
    }
  }

}
