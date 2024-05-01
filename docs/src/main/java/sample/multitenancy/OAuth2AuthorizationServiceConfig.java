/*
 * Copyright 2020-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sample.multitenancy;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;

@Configuration(proxyBeanMethods = false)
public class OAuth2AuthorizationServiceConfig {

	@Bean
	public OAuth2AuthorizationService authorizationService(
			@Qualifier("issuer1-data-source") DataSource issuer1DataSource,
			@Qualifier("issuer2-data-source") DataSource issuer2DataSource,
			RegisteredClientRepository registeredClientRepository) {

		Map<String, OAuth2AuthorizationService> authorizationServiceMap = new HashMap<>();
		authorizationServiceMap.put("issuer1", new JdbcOAuth2AuthorizationService(	// <1>
				new JdbcTemplate(issuer1DataSource), registeredClientRepository));
		authorizationServiceMap.put("issuer2", new JdbcOAuth2AuthorizationService(	// <2>
				new JdbcTemplate(issuer2DataSource), registeredClientRepository));

		return new DelegatingOAuth2AuthorizationService(authorizationServiceMap);
	}

	private static class DelegatingOAuth2AuthorizationService implements OAuth2AuthorizationService {	// <3>
		private final Map<String, OAuth2AuthorizationService> authorizationServiceMap;

		private DelegatingOAuth2AuthorizationService(Map<String, OAuth2AuthorizationService> authorizationServiceMap) {
			this.authorizationServiceMap = authorizationServiceMap;
		}

		@Override
		public void save(OAuth2Authorization authorization) {
			OAuth2AuthorizationService authorizationService = getAuthorizationService();
			if (authorizationService != null) {
				authorizationService.save(authorization);
			}
		}

		@Override
		public void remove(OAuth2Authorization authorization) {
			OAuth2AuthorizationService authorizationService = getAuthorizationService();
			if (authorizationService != null) {
				authorizationService.remove(authorization);
			}
		}

		@Override
		public OAuth2Authorization findById(String id) {
			OAuth2AuthorizationService authorizationService = getAuthorizationService();
			return (authorizationService != null) ?
					authorizationService.findById(id) :
					null;
		}

		@Override
		public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
			OAuth2AuthorizationService authorizationService = getAuthorizationService();
			return (authorizationService != null) ?
					authorizationService.findByToken(token, tokenType) :
					null;
		}

		private OAuth2AuthorizationService getAuthorizationService() {
			if (AuthorizationServerContextHolder.getContext() == null ||
					AuthorizationServerContextHolder.getContext().getIssuer() == null) {
				return null;
			}
			String issuer = AuthorizationServerContextHolder.getContext().getIssuer();	// <4>
			for (Map.Entry<String, OAuth2AuthorizationService> entry : this.authorizationServiceMap.entrySet()) {
				if (issuer.endsWith(entry.getKey())) {
					return entry.getValue();
				}
			}
			return null;
		}

	}

}