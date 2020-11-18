package mobi.cangol.plugin.seafile;

import com.android.tools.r8.code.A;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

class UploadClient {
    private static final Log log = LogFactory.getLog(UploadClient.class);
    private UploadPluginExtension extension;
    private static UploadClient seaClient;
    static UploadClient init(UploadPluginExtension extension){
        if(seaClient==null){
            seaClient=new UploadClient(extension);
        }
        return seaClient;
    }

    public UploadClient(UploadPluginExtension extension){
        this.extension=extension;
    }

    String upload(String link,String destDir,String destFileName,String srcFilePath) {
        HttpClient httpClient=getUnSafeHttpClient();
        HttpPost httpPost = new HttpPost(link);
        try {
            httpPost.setHeader("Authorization", "Token "+extension.getToken());
            httpPost.setHeader("contentType", "multipart/form-data");
            MultipartEntity entity=new MultipartEntity();
            entity.addPart("parent_dir", new StringBody(destDir, Charset.forName("UTF-8")));
            entity.addPart("replace", new StringBody("1", Charset.forName("UTF-8")));
            entity.addPart("file",new InputStreamBody(new FileInputStream(srcFilePath),destFileName));
            httpPost.setEntity(entity);
            HttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if(statusCode==200){
                String result =null;
                if(response.getEntity() != null) {
                    result = EntityUtils.toString(new BufferedHttpEntity(response.getEntity()), "UTF-8");
                }
                log.info("upload result="+ result);
                return result;
            }else
                log.error("upload statusCode="+ statusCode+ ","+response.getEntity().toString());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
        return null;
    }

    String createDir(String destDir) {
        HttpClient httpClient=getUnSafeHttpClient();
        HttpPost httpPost = new HttpPost(extension.getServer()+"/api2/repos/"+extension.getRepo()+"/dir/?p="+destDir);
        try {
            httpPost.setHeader("Authorization", "Token "+extension.getToken());
            List<NameValuePair> pairs=new ArrayList<>();
            pairs.add(new BasicNameValuePair("operation", "mkdir"));
            UrlEncodedFormEntity entity=new UrlEncodedFormEntity(pairs);
            httpPost.setEntity(entity);
            HttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if(statusCode==201){
                String result =null;
                if(response.getEntity() != null) {
                    result = EntityUtils.toString(new BufferedHttpEntity(response.getEntity()), "UTF-8");
                }
                log.info("createDir result="+ result);
                return destDir;
            }else
                log.error("createDir statusCode="+ statusCode+ ","+response.getEntity().toString());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
        return null;
    }

    String getUploadLink(String destDir) {
        HttpClient httpClient=getUnSafeHttpClient();
        HttpGet httpGet = new HttpGet(extension.getServer()+"/api2/repos/"+extension.getRepo()+"/upload-link/?p="+destDir);
        try {
            httpGet.setHeader("Authorization", "Token "+extension.getToken());
            HttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            if(statusCode==200){
                String result =null;
                if(response.getEntity() != null) {
                    result = EntityUtils.toString(new BufferedHttpEntity(response.getEntity()), "UTF-8");
                    result= result.replaceAll("\"","");
                }
                log.info("getUploadLink result="+ result);
                return result;
            }else{
                log.error("getUploadLink statusCode="+ statusCode+ ","+response.getEntity().toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
        return null;
    }
    public  HttpClient getUnSafeHttpClient() {
        SSLContext sslContext = null;
        X509TrustManager trustManager = new UnSafeTrustManager();
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
        } catch (Exception e) {
            log.info("getUnSafeOkHttp:" + e.getMessage());
        }
        return HttpClientBuilder.create()
                .setHostnameVerifier(new X509HostnameVerifier(){

                    @Override
                    public boolean verify(String s, SSLSession sslSession) {
                        return true;
                    }

                    @Override
                    public void verify(String host, SSLSocket ssl) throws IOException {

                    }

                    @Override
                    public void verify(String host, X509Certificate cert) throws SSLException {

                    }

                    @Override
                    public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {

                    }
                })
                .setSSLSocketFactory(new MyConnectionSocketFactory(sslContext))
                .build();
    }
    static class MyConnectionSocketFactory extends SSLConnectionSocketFactory {
        private SSLContext sslContext;
        public MyConnectionSocketFactory(final SSLContext sslContext) {
            super(sslContext);
            this.sslContext=sslContext;
        }

        @Override
        public Socket createSocket(final HttpContext context) throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }
     static class UnSafeTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }
    }
}