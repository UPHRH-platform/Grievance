package org.upsmf.grievance.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

	@Value("${urls.whitelist}")
	private String whitelistUrls;

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
				logger.warn("couldn't find bearer string, will ignore the header");
			}
			if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				if (validateToken(authToken)) {
					authorized = Boolean.TRUE;
				}
			}
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

	private boolean validateToken(String authToken) {
		Claims claims = Jwts.parser().parseClaimsJws(authToken).getBody();
		Long exp = (Long) claims.get("exp");
		Instant instant = Instant.ofEpochSecond(exp);
		ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.of(ZoneOffset.SHORT_IDS.get("IST")));
		ZonedDateTime currDt = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of(ZoneOffset.SHORT_IDS.get("IST")));
		if(zdt.isAfter(currDt)){
			return Boolean.TRUE;
		} else {
			return Boolean.FALSE;
		}
	}

	public String getUserName(String authToken) {
		return getUsernameFromToken(authToken);
	}

	private String getUsernameFromToken(String authToken) {
		if(validateToken(authToken)) {
			Claims claims = Jwts.parser().parseClaimsJws(authToken).getBody();
			return claims.get("username").toString();
		}
		return null;
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