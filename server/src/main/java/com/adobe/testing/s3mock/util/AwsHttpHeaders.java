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

package com.adobe.testing.s3mock.util;

/**
 * Holds Header used in HTTP requests from AWS S3 Client.
 */
public final class AwsHttpHeaders {

  private static final String NOT = "!";

  public static final String X_AMZ_SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID =
      "x-amz-server-side-encryption-aws-kms-key-id";

  public static final String X_AMZ_SERVER_SIDE_ENCRYPTION = "x-amz-server-side-encryption";
  public static final String NOT_X_AMZ_SERVER_SIDE_ENCRYPTION = NOT + X_AMZ_SERVER_SIDE_ENCRYPTION;

  public static final String RANGE = "Range";

  public static final String X_AMZ_COPY_SOURCE = "x-amz-copy-source";
  public static final String NOT_X_AMZ_COPY_SOURCE = NOT + X_AMZ_COPY_SOURCE;

  public static final String X_AMZ_COPY_SOURCE_RANGE = "x-amz-copy-source-range";
  public static final String NOT_X_AMZ_COPY_SOURCE_RANGE = NOT + X_AMZ_COPY_SOURCE_RANGE;

  public static final String X_AMZ_METADATA_DIRECTIVE = "x-amz-metadata-directive";

  public static final String X_AMZ_CONTENT_SHA256 = "x-amz-content-sha256";
  public static final String X_AMZ_TAGGING = "x-amz-tagging";
  public static final String CONTENT_MD5 = "Content-MD5";

  private AwsHttpHeaders() {
    // empty private constructor
  }
}
