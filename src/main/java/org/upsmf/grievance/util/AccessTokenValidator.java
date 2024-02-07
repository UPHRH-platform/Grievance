package org.upsmf.grievance.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.keycloak.common.util.Time;
import org.upsmf.grievance.constants.JsonKey;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

@Slf4j
public class AccessTokenValidator {

    private static ObjectMapper mapper = new ObjectMapper();

    private static Map<String, Object> validateToken(String token, boolean checkActive) throws Exception {
        String[] tokenElements = token.split("\\.");
        String header = tokenElements[0];
        String body = tokenElements[1];
        String signature = tokenElements[2];
        String payLoad = header + JsonKey.DOT_SEPARATOR + body;
        Map<Object, Object> headerData =
                mapper.readValue(new String(decodeFromBase64(header)), Map.class);
        String keyId = headerData.get("kid").toString();
        String keyString = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlMonTaXuJ5Pjs2RGaRhejdFLUjxDankogDEazurumwFvWzIqZrEabqc0Nqur4zu+/ZzYBn4gTbmdBDiJ4rZCQflQ+53KM5nK124AQ8zArZkR1Zmkxwh7TgZrC0jkov97ySXfyMfbbO3cnSqKQHXqt1NF6iBufz7W3nhOMUaJ939QYxCwA01JRhAWtl6tJ1Q7PkSPKGHdzJZJIwRRd4Vu8aXXz/wT/9kkDI2tLj9aQROVJrs9MWCzxvmHsQNWnZ/dD75MmI+YmtNUhC1EOmRfzM+NBGqwk04VTN10j0BhDABBBLTExiFGBi2VnSQW2bQ05Gf/9NXqYbXH8HbWGlAQlwIDAQAB";
        // return body
        return mapper.readValue(new String(decodeFromBase64(body)), Map.class);
        /*boolean isValid =
                CryptoUtil.verifyRSASign(
                        payLoad,
                        decodeFromBase64(signature),
                        KeyManager.loadPublicKey(keyString),
                        JsonKey.SHA_256_WITH_RSA);
        if (isValid) {
            Map<String, Object> tokenBody =
                    mapper.readValue(new String(decodeFromBase64(body)), Map.class);
            if(checkActive) {
                boolean isExp = isExpired((Integer) tokenBody.get("exp"));
                if (isExp) {
                    return Collections.EMPTY_MAP;
                }
            }
            return tokenBody;
        }
        return Collections.EMPTY_MAP;
        */
    }

    /**
     * managedtoken is validated and requestedByUserID, requestedForUserID values are validated
     * aganist the managedEncToken
     *
     * @param managedEncToken
     * @param requestedByUserId
     * @param requestedForUserId
     * @return
     */
    public static String verifyManagedUserToken(
            String managedEncToken, String requestedByUserId, String requestedForUserId, String loggingHeaders) {
        String managedFor = JsonKey.UNAUTHORIZED;
        try {
            Map<String, Object> payload = validateToken(managedEncToken, true);
            if (MapUtils.isNotEmpty(payload)) {
                String parentId = (String) payload.get(JsonKey.PARENT_ID);
                String muaId = (String) payload.get(JsonKey.SUB);
                log.info(
                        "AccessTokenValidator: parent uuid: "
                                + parentId
                                + " managedBy uuid: "
                                + muaId
                                + " requestedByUserID: "
                                + requestedByUserId
                                + " requestedForUserId: "
                                + requestedForUserId);
                boolean isValid =
                        parentId.equalsIgnoreCase(requestedByUserId);
                if(!muaId.equalsIgnoreCase(requestedForUserId)) {
                    log.info("RequestedFor userid : " + requestedForUserId + " is not matching with the muaId : " + muaId + " Headers: " + loggingHeaders);
                }
                if (isValid) {
                    managedFor = muaId;
                }
            }
        } catch (Exception ex) {
            log.error( "Exception in AccessTokenValidator: verify ", ex);
        }
        return managedFor;
    }

    public static String verifyUserToken(String token, boolean checkActive) {
        String userId = JsonKey.UNAUTHORIZED;
        try {
            Map<String, Object> payload = validateToken(token, checkActive);
            if (MapUtils.isNotEmpty(payload) && checkIss((String) payload.get("iss"))) {
                userId = (String) payload.get(JsonKey.PREFERRED_USERNAME);
            }
        } catch (Exception ex) {
            log.error( "Exception in verifyUserAccessToken: verify ", ex);
        }
        return userId;
    }

    private static boolean checkIss(String iss) {
        /*String realmUrl =
                KeyCloakConnectionProvider.SSO_URL + "realms/" + KeyCloakConnectionProvider.SSO_REALM;
        return (realmUrl.equalsIgnoreCase(iss));*/
        return Boolean.TRUE;
    }

    private static boolean isExpired(Integer expiration) {
        return (Time.currentTime() > expiration);
    }

    private static byte[] decodeFromBase64(String data) {
        return Base64.getDecoder().decode(data.getBytes(Charset.forName("UTF-8")));
    }
}
