/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.storage.gcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.codelibs.fess.exception.StorageException;
import org.codelibs.fess.storage.StorageItem;
import org.junit.jupiter.api.Test;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;

public class GcsStorageClientTest {

    /** Uses the protected constructor, which builds no SDK client, so no network is touched. */
    private static final class TestableGcsStorageClient extends GcsStorageClient {
        TestableGcsStorageClient() {
            super();
        }
    }

    @Test
    public void test_isDefaultEndpoint() {
        try (GcsStorageClient client = new TestableGcsStorageClient()) {
            assertTrue(client.isDefaultEndpoint("https://storage.googleapis.com"));
            assertTrue(client.isDefaultEndpoint("https://storage.googleapis.com/"));
            assertTrue(client.isDefaultEndpoint("storage.googleapis.com"));
            assertTrue(client.isDefaultEndpoint("https://storage.googleapis.com/storage/v1"));
            assertTrue(client.isDefaultEndpoint("https://storage.googleapis.com:443"));

            assertFalse(client.isDefaultEndpoint(""));
            assertFalse(client.isDefaultEndpoint("http://storage.googleapis.com"));
            assertFalse(client.isDefaultEndpoint("https://storage.googleapis.com:4443"));
            assertFalse(client.isDefaultEndpoint("http://localhost:4443"));
            assertFalse(client.isDefaultEndpoint("https://example.com/storage.googleapis.com"));
        }
    }

    /** Injects a {@link Storage} whose {@code list} call is answered by the given supplier. */
    private static GcsStorageClient clientListing(final String bucket, final Supplier<Page<Blob>> listing) throws Exception {
        final Storage storage = (Storage) Proxy.newProxyInstance(Storage.class.getClassLoader(), new Class<?>[] { Storage.class },
                (proxy, method, args) -> {
                    if ("list".equals(method.getName())) {
                        return listing.get();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        final GcsStorageClient client = new TestableGcsStorageClient();
        setField(client, "storage", storage);
        setField(client, "bucket", bucket);
        return client;
    }

    private static void setField(final GcsStorageClient client, final String name, final Object value) throws Exception {
        final Field field = GcsStorageClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(client, value);
    }

    /** A page without blobs that fails while it is being iterated when given a failure, as a connection lost between two page requests does. */
    private static final class StubPage implements Page<Blob> {
        private final RuntimeException failure;

        StubPage(final RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public boolean hasNextPage() {
            return false;
        }

        @Override
        public String getNextPageToken() {
            return null;
        }

        @Override
        public Page<Blob> getNextPage() {
            return null;
        }

        @Override
        public Iterable<Blob> iterateAll() {
            if (failure != null) {
                throw failure;
            }
            return Collections.emptyList();
        }

        @Override
        public Iterable<Blob> getValues() {
            return Collections.emptyList();
        }
    }

    /** A failed listing must not read as an empty bucket: the caller has to be able to tell the two apart. */
    @Test
    public void test_listObjects_failureBecomesStorageException() throws Exception {
        final RuntimeException failure = new IllegalStateException("connection refused");
        try (GcsStorageClient client = clientListing("my-bucket", () -> {
            throw failure;
        })) {
            final StorageException e = assertThrows(StorageException.class, () -> client.listObjects("docs", 10));
            assertTrue(e.getMessage().contains("my-bucket"), e.getMessage());
            assertTrue(e.getMessage().contains("docs"), e.getMessage());
            assertSame(failure, e.getCause());
        }
    }

    @Test
    public void test_listObjects_failureWhileIteratingBecomesStorageException() throws Exception {
        final RuntimeException failure = new IllegalStateException("connection reset");
        try (GcsStorageClient client = clientListing("my-bucket", () -> new StubPage(failure))) {
            final StorageException e = assertThrows(StorageException.class, () -> client.listObjects(null, 10));
            assertTrue(e.getMessage().contains("my-bucket"), e.getMessage());
            assertSame(failure, e.getCause());
        }
    }

    /** An existing but empty bucket still lists as empty. */
    @Test
    public void test_listObjects_emptyBucket() throws Exception {
        try (GcsStorageClient client = clientListing("my-bucket", () -> new StubPage(null))) {
            final List<StorageItem> items = client.listObjects("docs", 10);
            assertEquals(0, items.size());
        }
    }
}
