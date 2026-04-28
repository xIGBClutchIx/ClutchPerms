package me.clutchy.clutchperms.common.command;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCacheSubjectLookupTest {

    @Test
    void asyncProfileCacheLookupResolvesSubject() {
        UUID subjectId = UUID.randomUUID();
        CompletableFuture<Optional<CommandSubject>> future = ProfileCacheSubjectLookup
                .resolveSubjectAsync(new AsyncServer(new AsyncProfileCache(new FakeProfile(subjectId, "RemoteUser"))), "RemoteUser");

        assertEquals(Optional.of(new CommandSubject(subjectId, "RemoteUser")), future.join());
    }

    @Test
    void syncUserCacheLookupFallsBackWhenAsyncLookupIsUnavailable() {
        UUID subjectId = UUID.randomUUID();
        CompletableFuture<Optional<CommandSubject>> future = ProfileCacheSubjectLookup
                .resolveSubjectAsync(new UserCacheServer(new UserCache(new FakeProfile(subjectId, "StoredUser"))), "StoredUser");

        assertEquals(Optional.of(new CommandSubject(subjectId, "StoredUser")), future.join());
    }

    @Test
    void missingProfileCacheReturnsEmpty() {
        CompletableFuture<Optional<CommandSubject>> future = ProfileCacheSubjectLookup.resolveSubjectAsync(new Object(), "MissingUser");

        assertEquals(Optional.empty(), future.join());
    }

    @Test
    void lookupFailuresPropagate() {
        RuntimeException failure = new RuntimeException("lookup failed");
        CompletableFuture<Optional<CommandSubject>> future = ProfileCacheSubjectLookup.resolveSubjectAsync(new AsyncServer(new FailingAsyncProfileCache(failure)), "BrokenUser");

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertSame(failure, exception.getCause());
    }

    @Test
    void invalidProfileRowsAreRejected() {
        CompletableFuture<Optional<CommandSubject>> future = ProfileCacheSubjectLookup
                .resolveSubjectAsync(new AsyncServer(new AsyncProfileCache(new FakeProfile(UUID.randomUUID(), "   "))), "BrokenUser");

        assertTrue(future.join().isEmpty());
    }

    private record AsyncServer(Object profileCache) {

        public Object getProfileCache() {
            return profileCache;
        }
    }

    private record UserCacheServer(UserCache userCache) {

        public UserCache getUserCache() {
            return userCache;
        }
    }

    private record AsyncProfileCache(FakeProfile profile) {

        public CompletableFuture<Optional<FakeProfile>> getAsync(String target) {
            return CompletableFuture.completedFuture(Optional.of(profile));
        }
    }

    private record FailingAsyncProfileCache(RuntimeException failure) {

        public CompletableFuture<Optional<FakeProfile>> getAsync(String target) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private record UserCache(FakeProfile profile) {

        public Optional<FakeProfile> getOfflinePlayerProfile(String target) {
            return Optional.of(profile);
        }
    }

    private record FakeProfile(UUID id, String name) {

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
