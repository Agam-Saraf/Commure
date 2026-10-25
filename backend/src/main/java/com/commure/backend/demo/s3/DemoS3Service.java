package com.commure.backend.demo.s3;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
@Profile("s3")
public class DemoS3Service {

	private final S3Client s3Client;
	private final DemoS3Properties properties;

	public DemoS3Service(S3Client s3Client, DemoS3Properties properties) {
		this.s3Client = s3Client;
		this.properties = properties;
	}

	public void ensureBucket() {
		String bucket = properties.getBucket();
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
		}
		catch (NoSuchBucketException ex) {
			s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
			}
			else {
				throw ex;
			}
		}
	}

	public void putText(String key, String body) {
		ensureBucket();
		s3Client.putObject(
				PutObjectRequest.builder()
						.bucket(properties.getBucket())
						.key(key)
						.contentType("text/plain")
						.build(),
				RequestBody.fromString(body, StandardCharsets.UTF_8));
	}

	public String getText(String key) {
		var bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(properties.getBucket())
				.key(key)
				.build());
		return bytes.asString(StandardCharsets.UTF_8);
	}

	public List<String> listKeys() {
		ensureBucket();
		return s3Client.listObjectsV2(ListObjectsV2Request.builder()
				.bucket(properties.getBucket())
				.build())
				.contents()
				.stream()
				.map(S3Object::key)
				.toList();
	}

	public String bucket() {
		return properties.getBucket();
	}
}
