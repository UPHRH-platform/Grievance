package org.upsmf.grievance.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Base64Utils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.PublicKey;

@Slf4j
public class KeyManager {


   /* public static void init() {
        String basePath = propertiesCache.getProperty(JsonKey.ACCESS_TOKEN_PUBLICKEY_BASEPATH);
        try (Stream<Path> walk = Files.walk(Paths.get(basePath))) {
            List<String> result =
                    walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());
            result.forEach(
                    file -> {
                        try {
                            StringBuilder contentBuilder = new StringBuilder();
                            Path path = Paths.get(file);
                            Files.lines(path, StandardCharsets.UTF_8)
                                    .forEach(
                                            x -> {
                                                contentBuilder.append(x);
                                            });
                            KeyData keyData =
                                    new KeyData(
                                            path.getFileName().toString(), loadPublicKey(contentBuilder.toString()));
                            keyMap.put(path.getFileName().toString(), keyData);
                        } catch (Exception e) {
                            logger.error(null,"KeyManager:init: exception in reading public keys ", e);
                        }
                    });
        } catch (Exception e) {
            logger.error(null,"KeyManager:init: exception in loading publickeys ", e);
        }
    }*/



    public static PublicKey loadPublicKey(String key) throws Exception {
        String publicKey = new String(key.getBytes(), StandardCharsets.UTF_8);
        publicKey = publicKey.replaceAll("(-+BEGIN PUBLIC KEY-+)", "");
        publicKey = publicKey.replaceAll("(-+END PUBLIC KEY-+)", "");
        publicKey = publicKey.replaceAll("[\\r\\n]+", "");
        byte[] keyBytes = Base64Utils.decode(publicKey.getBytes("UTF-8"));

        X509EncodedKeySpec X509publicKey = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(X509publicKey);
    }

}
