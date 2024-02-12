package org.upsmf.grievance.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.upsmf.grievance.constants.JsonKey;
import org.upsmf.grievance.util.AccessTokenValidator;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	@Value("${urls.whitelist}")
	private String whitelistUrls;

	@Autowired
	private StringRedisTemplate redisTemplate;

	public static final String HEADER_AUTHENTICATION = "Authorization";
	public static final String STATUS_CODE = "statusCode";
	public static final String STATUS = "statusInfo";
	public static final String STATUS_MESSAGE = "statusMessage";
	public static final String ERROR_MESSAGE = "errorMessage";
	public static final String TOKEN_PREFIX = "Bearer ";

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws IOException, ServletException {

		
		List<String> whitelistUrlList = Arrays.asList(whitelistUrls.split(","));

		ServletContext ctx = req.getServletContext();
		Boolean whiteListed;
		Boolean authorized = Boolean.FALSE;
		String username = null;
		String authToken = null;
		Map<String, Object> userInfoObectMap = new HashMap<>();

		if (whitelistUrlList.contains(req.getRequestURI())) {
			whiteListed = Boolean.TRUE;
		} else {
			whiteListed = Boolean.FALSE;
			String header = req.getHeader(HEADER_AUTHENTICATION);
			log.info("Auth header - {}", header);
			if (StringUtils.isBlank(header)) {
				res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				res.getWriter()
						.write(failureResponse(HttpStatus.UNAUTHORIZED.getReasonPhrase()));
				res.setContentType("application/json");
				res.getWriter().flush();
				return;
			}
			if (header.startsWith(TOKEN_PREFIX)) {
				authToken = header.replace(TOKEN_PREFIX, "");
				username = getUserName(authToken);
			} else {
				log.error("couldn't find bearer string, will ignore the header");
			}
			// check token in REDIS
			String persistedToken = redisTemplate.opsForValue().get(username);
			log.info("Auth header found in REDIS  - {}", persistedToken);
			if(persistedToken == null || persistedToken.isEmpty()
					|| !persistedToken.equals(authToken)) {
				res.setStatus(HttpServletResponse.SC_FORBIDDEN);
				res.getWriter()
						.write(failureResponse(HttpStatus.FORBIDDEN.getReasonPhrase()));
				res.setContentType("application/json");
				res.getWriter().flush();
				return;
			}
			// setting true
			authorized = Boolean.TRUE;

		}

		if (!authorized && !whiteListed) {
			res.setStatus(HttpServletResponse.SC_FORBIDDEN);
			res.getWriter()
					.write(failureResponse(HttpStatus.FORBIDDEN.getReasonPhrase()));
			res.setContentType("application/json");
			res.getWriter().flush();
			return;
		}

		chain.doFilter(req, res);
	}

	public String getUserName(String authToken) {
		return getUsernameFromToken(authToken);
	}

	private String getUsernameFromToken(String authToken) {
		String userId = AccessTokenValidator.verifyUserToken(authToken, true);
		if (userId.equalsIgnoreCase(JsonKey.UNAUTHORIZED)) {
			return null;
		}
		return userId;
	}

	public static String failureResponse(String message) throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();
		ObjectNode actualResponse = objectMapper.createObjectNode();

		ObjectNode response = objectMapper.createObjectNode();
		response.put(STATUS_CODE, HttpStatus.BAD_REQUEST.value());
		response.put(STATUS_MESSAGE, HttpStatus.BAD_REQUEST.getReasonPhrase());
		response.put(ERROR_MESSAGE, message);
		actualResponse.putPOJO(STATUS, response);

		return objectMapper.writeValueAsString(actualResponse);
	}

}