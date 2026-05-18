package nbc.profile.shared.application.port.out;

import java.net.URL;
import java.time.Duration;

public interface FileStoragePort {

    URL upload(String key, byte[] content, String contentType);

    byte[] download(String key);

    void delete(String key);

    boolean exists(String key);

    URL generatePresignedUrl(String key, Duration expiration);
}
