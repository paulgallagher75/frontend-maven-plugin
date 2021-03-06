package com.github.eirslett.maven.plugins.frontend.lib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig.Proxy;

final class DownloadException extends Exception {
    public DownloadException(String message){
        super(message);
    }
    DownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}

interface FileDownloader {
    void download(String downloadUrl, String destination) throws DownloadException;
}

final class DefaultFileDownloader implements FileDownloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileDownloader.class);

    private final ProxyConfig proxyConfig;

    public DefaultFileDownloader(ProxyConfig proxyConfig){
        this.proxyConfig = proxyConfig;
    }

    public void download(String downloadUrl, String destination) throws DownloadException {
        String fixedDownloadUrl = downloadUrl;
        try {
            fixedDownloadUrl = FilenameUtils.separatorsToUnix(fixedDownloadUrl);
            URI downloadURI = new URI(fixedDownloadUrl);
            if ("file".equalsIgnoreCase(downloadURI.getScheme())) {
                FileUtils.copyFile(new File(downloadURI), new File(destination));
            }
            else {
                CloseableHttpResponse response = execute(fixedDownloadUrl);
                int statusCode = response.getStatusLine().getStatusCode();
                if(statusCode != 200){
                    throw new DownloadException("Got error code "+ statusCode +" from the server.");
                }
                new File(FilenameUtils.getFullPathNoEndSeparator(destination)).mkdirs();
                ReadableByteChannel rbc = Channels.newChannel(response.getEntity().getContent());
                FileOutputStream fos = new FileOutputStream(destination);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                fos.close();
            }
        } catch (IOException e) {
            throw new DownloadException("Could not download "+fixedDownloadUrl, e);
        }
        catch (URISyntaxException e) {
            throw new DownloadException("Could not download "+fixedDownloadUrl, e);
        }
    }

    private CloseableHttpResponse execute(String requestUrl) throws IOException {
        CloseableHttpResponse response;
        Proxy proxy = proxyConfig.getProxyForUrl(requestUrl);
        if (proxy != null) {
            LOGGER.info("Downloading via proxy " + proxy.toString());
            return executeViaProxy(proxy, requestUrl);
        } else {
            LOGGER.info("No proxy was configured, downloading directly");
            response = buildHttpClient(null).execute(new HttpGet(requestUrl));
        }
        return response;
    }

    private CloseableHttpResponse executeViaProxy(Proxy proxy, String requestUrl) throws IOException {
        final CloseableHttpClient proxyClient;
        if (proxy.useAuthentication()){
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(proxy.host, proxy.port),
                    new UsernamePasswordCredentials(proxy.username, proxy.password)
            );
            proxyClient = buildHttpClient(credentialsProvider);
        } else {
            proxyClient = buildHttpClient(null);
        }

        final HttpHost proxyHttpHost = new HttpHost(proxy.host, proxy.port, proxy.protocol);

        final RequestConfig requestConfig = RequestConfig.custom().setProxy(proxyHttpHost).build();

        final HttpGet request = new HttpGet(requestUrl);
        request.setConfig(requestConfig);

        return proxyClient.execute(request);
    }
    
    private CloseableHttpClient buildHttpClient(CredentialsProvider credentialsProvider) {
    	return HttpClients.custom()
    			.disableContentCompression()
    			.setDefaultCredentialsProvider(credentialsProvider)
    			.build();
    }
    
}
