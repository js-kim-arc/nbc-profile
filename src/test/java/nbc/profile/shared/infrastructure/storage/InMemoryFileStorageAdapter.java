package nbc.profile.shared.infrastructure.storage;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nbc.profile.common.exception.ErrorCode;
import nbc.profile.shared.application.port.out.FileStorageException;
import nbc.profile.shared.application.port.out.FileStoragePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryFileStorageAdapter implements FileStoragePort {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public URL upload(String key, byte[] content, String contentType) {
        store.put(key, content);
        return toUrl(key);
    }

    @Override
    public byte[] download(String key) {
        byte[] bytes = store.get(key);
        if (bytes == null) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_NOT_FOUND);
        }
        return bytes;
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }

    @Override
    public URL generatePresignedUrl(String key, Duration expiration) {
        return toUrl(key);
    }

    private URL toUrl(String key) {
        try {
            return URI.create("http://in-memory.test/" + key).toURL();
        } catch (MalformedURLException e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_UPLOAD_FAILED, e);
        }
    }
}
