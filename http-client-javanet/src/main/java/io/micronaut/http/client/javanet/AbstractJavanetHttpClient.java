/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.javanet;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ContextPathUtils;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.http.ssl.SslConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Optional;

import static io.micronaut.http.client.exceptions.HttpClientExceptionUtils.populateServiceId;

/*
 * TODO: HttpClient defaults to netty ByteBuffer as a type for exchange which isn't best for here.
 */
@Experimental
abstract class AbstractJavanetHttpClient {

    protected final LoadBalancer loadBalancer;
    protected final HttpVersionSelection httpVersion;
    protected final HttpClientConfiguration configuration;
    protected final String contextPath;
    protected final HttpClient client;
    protected final CookieManager cookieManager;
    protected final RequestBinderRegistry requestBinderRegistry;
    protected final String clientId;
    protected final ConversionService conversionService;
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected final JavanetClientSslBuilder sslBuilder;

    private final Logger log;

    protected AbstractJavanetHttpClient(
        Logger log,
        LoadBalancer loadBalancer,
        HttpVersionSelection httpVersion,
        HttpClientConfiguration configuration,
        String contextPath,
        MediaTypeCodecRegistry mediaTypeCodecRegistry,
        RequestBinderRegistry requestBinderRegistry,
        String clientId,
        ConversionService conversionService,
        JavanetClientSslBuilder sslBuilder
    ) {
        this.log = configuration.getLoggerName().map(LoggerFactory::getLogger).orElse(log);
        this.loadBalancer = loadBalancer;
        this.httpVersion = httpVersion;
        this.configuration = configuration;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.requestBinderRegistry = requestBinderRegistry;
        this.clientId = clientId;
        this.conversionService = conversionService;
        this.cookieManager = new CookieManager();
        this.sslBuilder = sslBuilder;

        if (StringUtils.isNotEmpty(contextPath)) {
            if (contextPath.charAt(0) != '/') {
                contextPath = '/' + contextPath;
            }
            this.contextPath = contextPath;
        } else {
            this.contextPath = null;
        }

        HttpClient.Builder builder = HttpClient.newBuilder();
        configuration.getConnectTimeout().ifPresent(builder::connectTimeout);

        builder
            .version(httpVersion != null && httpVersion.isAlpn() ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1)
            .followRedirects(configuration.isFollowRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
            .cookieHandler(cookieManager);

        Optional<SocketAddress> proxyAddress = configuration.getProxyAddress();
        if (proxyAddress.isPresent()) {
            SocketAddress socketAddress = proxyAddress.get();
            builder = configureProxy(builder, socketAddress, configuration.getProxyUsername().orElse(null), configuration.getProxyPassword().orElse(null));
        }

        if (configuration.getSslConfiguration() instanceof ClientSslConfiguration clientSslConfiguration) {
            configureSsl(builder, clientSslConfiguration);
        }

        this.client = builder.build();
    }

    private HttpClient.Builder configureProxy(
        @NonNull HttpClient.Builder builder,
        @NonNull SocketAddress address,
        @Nullable String username,
        @Nullable String password
    ) {
        if (log.isDebugEnabled()) {
            log.debug("Configuring proxy: {} with username: {}", address, username);
        }
        if (address instanceof InetSocketAddress inetSocketAddress) {
            builder = builder.proxy(ProxySelector.of(inetSocketAddress));
            if (username != null && password != null) {
                builder = builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                });
            }
        } else {
            throw new IllegalArgumentException("Unsupported proxy address type: " + address.getClass().getName());
        }
        return builder;
    }

    private void configureSsl(HttpClient.Builder builder, ClientSslConfiguration clientSslConfiguration) {
        sslBuilder.build(clientSslConfiguration).ifPresent(builder::sslContext);
        if (System.getProperty("jdk.internal.httpclient.disableHostnameVerification") != null && log.isWarnEnabled()) {
            log.warn("The jdk.internal.httpclient.disableHostnameVerification system property is set. This is not recommended for production use as it prevents proper certificate validation and may allow man-in-the-middle attacks.");
        }
        SSLParameters sslParameters = new SSLParameters();
        clientSslConfiguration.getClientAuthentication().ifPresent(a -> {
            if (a == ClientAuthentication.WANT) {
                sslParameters.setWantClientAuth(true);
            } else if (a == ClientAuthentication.NEED) {
                sslParameters.setNeedClientAuth(true);
            }
        });
        clientSslConfiguration.getProtocols().ifPresent(sslParameters::setProtocols);
        clientSslConfiguration.getCiphers().ifPresent(sslParameters::setCipherSuites);
        builder.sslParameters(sslParameters);
    }

    protected Object getLoadBalancerDiscriminator() {
        return null;
    }

    public MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
    }

    protected <I> Flux<HttpRequest> mapToHttpRequest(io.micronaut.http.HttpRequest<I> request, Argument<?> bodyType) {
        return resolveRequestUri(request)
            .map(uri -> {
                request.getCookies().getAll().forEach(cookie -> {
                    HttpCookie newCookie = HttpCookieUtils.of(cookie, request, uri.getHost());
                    cookieManager.getCookieStore().add(uri, newCookie);
                });

                return HttpRequestFactory.builder(uri, request, configuration, bodyType, mediaTypeCodecRegistry).build();
            });
    }

    private Flux<URI> resolveRequestUri(io.micronaut.http.HttpRequest<?> request) {
        if (request.getUri().getScheme() != null) {
            // Full request URI, so use that
            return Flux.just(request.getUri());
        }

        // Otherwise, go and look it up via the LoadBalancer
        return resolveURI(request);
    }

    private <I> Flux<URI> resolveURI(io.micronaut.http.HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (loadBalancer == null) {
            return Flux.error(populateServiceId(new NoHostException("Request URI specifies no host to connect to"), clientId, configuration));
        }

        return Flux.from(loadBalancer.select(getLoadBalancerDiscriminator())).map(server -> {
                Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                if (request instanceof MutableHttpRequest<?> mutableRequest && authInfo.isPresent()) {
                    mutableRequest.getHeaders().auth(authInfo.get());
                }

                try {
                    return server.resolve(ContextPathUtils.prepend(requestURI, contextPath));
                } catch (URISyntaxException e) {
                    throw populateServiceId(new HttpClientException("Failed to construct the request URI", e), clientId, configuration);
                }
            }
        );
    }

    @NonNull
    protected <O> HttpResponse<O> response(@NonNull java.net.http.HttpResponse<byte[]> netResponse, @NonNull Argument<O> bodyType) {
        return new HttpResponseAdapter<>(netResponse, bodyType, conversionService, mediaTypeCodecRegistry);
    }
}
