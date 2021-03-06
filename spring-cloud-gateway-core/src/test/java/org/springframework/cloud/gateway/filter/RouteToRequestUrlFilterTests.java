/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.Collections;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class RouteToRequestUrlFilterTests {

	@Test
	public void happyPath() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get?a=b")
				.build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost")
				.hasParameter("a", "b");
	}

	@Test
	public void noQueryParams() {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get")
				.build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost");
	}

	@Test
	public void encodedParameters() {
		URI url = UriComponentsBuilder.fromUriString("http://localhost/get?a=b&c=d[]").buildAndExpand().encode().toUri();

		// prove that it is encoded
		assertThat(url.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");

		assertThat(url).hasParameter("c", "d[]");

		MockServerHttpRequest request = MockServerHttpRequest
				.method(HttpMethod.GET, url)
				.build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost")
				.hasParameter("a", "b")
				.hasParameter("c", "d[]");

		// prove that it is not double encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");
	}

	@Test
	public void unencodedParameters() {
		URI url = URI.create("http://localhost/get?a=b&c=d[]");

		// prove that it is unencoded
		assertThat(url.getRawQuery()).isEqualTo("a=b&c=d[]");

		MockServerHttpRequest request = MockServerHttpRequest
				.method(HttpMethod.GET, url)
				.build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");

		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost")
				.hasParameter("a", "b")
				.hasParameter("c", "d[]");

		// prove that it is NOT encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d[]");
	}

	private ServerWebExchange testFilter(MockServerHttpRequest request, String url) {
		Route value = new Route("1", URI.create(url), 0,
				swe -> true, Collections.emptyList());

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, value);

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		RouteToRequestUrlFilter filter = new RouteToRequestUrlFilter();
		filter.filter(exchange, filterChain);

		return captor.getValue();
	}
}
