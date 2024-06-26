package mobi.cangol.plugin.seafile

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.client.HttpClient
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.X509HostnameVerifier
import org.apache.http.entity.BufferedHttpEntity
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import org.apache.http.protocol.HttpContext
import org.apache.http.util.EntityUtils
import org.json.simple.JSONObject
import org.json.simple.JSONValue

import javax.net.ssl.*
import java.nio.charset.Charset
import java.security.*
import java.security.cert.*

class UploadClient {
    private static final Log log = LogFactory.getLog(UploadClient.class)
    private UploadPluginExtension extension
    private static UploadClient seaClient

    static UploadClient init(UploadPluginExtension extension) {
        if (seaClient == null) {
            seaClient = new UploadClient(extension)
        }
        return seaClient
    }

    UploadClient(UploadPluginExtension extension) {
        this.extension = extension
    }

    String upload(String link, String destDir, String destFileName, String srcFilePath) {
        HttpClient httpClient = getUnSafeHttpClient()
        HttpPost httpPost = new HttpPost(link)
        try {
            httpPost.setHeader("Authorization", "Token " + extension.getToken())
            httpPost.setHeader("contentType", "multipart/form-data")
            MultipartEntity entity = new MultipartEntity()
            entity.addPart("parent_dir", new StringBody(destDir, Charset.forName("UTF-8")))
            entity.addPart("replace", new StringBody("1", Charset.forName("UTF-8")))
            entity.addPart("file", new InputStreamBody(new FileInputStream(srcFilePath), destFileName))
            httpPost.setEntity(entity)
            HttpResponse response = httpClient.execute(httpPost)
            int statusCode = response.getStatusLine().getStatusCode()
            if (statusCode == 200) {
                String result = null
                if (response.getEntity() != null) {
                    result = EntityUtils.toString(new BufferedHttpEntity(response.getEntity()), "UTF-8")
                }
                log.info("upload result=" + result)
                return result
            } else
                log.error("upload statusCode=" + statusCode + "," + response.getEntity().toString())
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            httpClient.getConnectionManager().shutdown()
        }
        return null
    }

    String createDir(String destDir) {
        HttpClient httpClient = getUnSafeHttpClient()
        HttpPost httpPost = new HttpPost(extension.getServer() + "/api2/repos/" + extension.getRepo() + "/dir/?p=" + destDir)
        try {
            httpPost.setHeader("Authorization", "Token " + extension.getToken())
            List<NameValuePair> pairs = new ArrayList<>()
            pairs.add(new BasicNameValuePair("operation", "mkdir"))
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(pairs)
            httpPost.setEntity(entity)
            HttpResponse response = httpClient.execute(httpPost)
            int statusCode = response.getStatusLine().getStatusCode()
            if (statusCode == 201) {
                String result = null
                if (response.getEntity() != null) {
                    result = EntityUtils.toString(new BufferedHttpEntity(response.getEntity()), "UTF-8")
                }
                log.info("createDir result=" + result)
                return destDir
            } else
                log.error("createDir statusCode=" + statusCode + "," + response.getEntity().toString())
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            httpClient.getConnectionManager().shutdown()
        }
        return null
    }

    String getUploadLink(String destDir) {
        HttpClient httpClient = getUnSafeHttpClient()
        HttpGet httpGet = new HttpGet(extension.getServer() + "/api2/repos/" + extension.getRepo() + "/upload-link/?p=" + destDir)
        try {
            httpGet.setHeader("Authorization", "Token " + extension.getToken())
            HttpResponse response = httpClient.execute(httpGet)
            int statusCode = response.getStatusLine().getStatusCode()
            if (statusCode == 200) {
                String result = null
                if (response.getEntity() != null) {
                    result = EntityUtils.toString(new BufferedHttpEntity(response.getEntity()), "UTF-8")
                    result = result.replaceAll("\"", "")
                }
                log.info("getUploadLink result=" + result)
                return result
            } else {
                log.error("getUploadLink statusCode=" + statusCode + "," + response.getEntity().toString())
            }
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            httpClient.getConnectionManager().shutdown()
        }
        return null
    }

    String getToken() {
        HttpClient httpClient = getUnSafeHttpClient()
        HttpPost httpPost = new HttpPost(extension.getServer() + "/api2/auth-token/")
        try {
            List<NameValuePair> pairs = new ArrayList<>()
            pairs.add(new BasicNameValuePair("username", extension.username))
            pairs.add(new BasicNameValuePair("password", extension.password))
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(pairs)
            httpPost.setEntity(entity)
            HttpResponse response = httpClient.execute(httpPost)
            int statusCode = response.getStatusLine().getStatusCode()
            if (statusCode == 200) {
                String result = null
                if (response.getEntity() != null) {
                    result = EntityUtils.toString(new BufferedHttpEntity(response.getEntity()), "UTF-8")
                    Object obj = JSONValue.parse(result);
                    JSONObject jsonObject = (JSONObject) obj;
                    result = (String) jsonObject.get("token")
                }
                log.info("getToken result=" + result)
                return result
            } else {
                log.error("getToken statusCode=" + statusCode + "," + response.getEntity().toString())
            }
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            httpClient.getConnectionManager().shutdown()
        }
        return null
    }

    HttpClient getUnSafeHttpClient() {
        SSLContext sslContext = null
        TrustManager[] trustManagers = [new UnSafeTrustManager()] as TrustManager[]
        try {
            sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustManagers, new SecureRandom())
        } catch (Exception e) {
            log.info("getUnSafeOkHttp:" + e.getMessage())
        }
        return HttpClientBuilder.create()
                .setHostnameVerifier(new X509HostnameVerifier() {

                    @Override
                    boolean verify(String s, SSLSession sslSession) {
                        return true
                    }

                    @Override
                    void verify(String host, SSLSocket ssl) throws IOException {

                    }

                    @Override
                    void verify(String host, X509Certificate cert) throws SSLException {

                    }

                    @Override
                    void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {

                    }
                })
                .setSSLSocketFactory(new MyConnectionSocketFactory(sslContext))
                .build()
    }

    static class MyConnectionSocketFactory extends SSLConnectionSocketFactory {
        private SSLContext sslContext

        MyConnectionSocketFactory(final SSLContext sslContext) {
            super(sslContext)
            this.sslContext = sslContext
        }

        @Override
        Socket createSocket(final HttpContext context) throws IOException {
            return sslContext.getSocketFactory().createSocket()
        }
    }

    static class UnSafeTrustManager implements X509TrustManager {
        @Override
        void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] _AcceptedIssuers = [new X509Certificate() {
                @Override
                void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {

                }

                @Override
                void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {

                }

                @Override
                int getVersion() {
                    return 0
                }

                @Override
                BigInteger getSerialNumber() {
                    return null
                }

                @Override
                Principal getIssuerDN() {
                    return null
                }

                @Override
                Principal getSubjectDN() {
                    return null
                }

                @Override
                Date getNotBefore() {
                    return null
                }

                @Override
                Date getNotAfter() {
                    return null
                }

                @Override
                byte[] getTBSCertificate() throws CertificateEncodingException {
                    return new byte[0]
                }

                @Override
                byte[] getSignature() {
                    return new byte[0]
                }

                @Override
                String getSigAlgName() {
                    return null
                }

                @Override
                String getSigAlgOID() {
                    return null
                }

                @Override
                byte[] getSigAlgParams() {
                    return new byte[0]
                }

                @Override
                boolean[] getIssuerUniqueID() {
                    return new boolean[0]
                }

                @Override
                boolean[] getSubjectUniqueID() {
                    return new boolean[0]
                }

                @Override
                boolean[] getKeyUsage() {
                    return new boolean[0]
                }

                @Override
                int getBasicConstraints() {
                    return 0
                }

                @Override
                byte[] getEncoded() throws CertificateEncodingException {
                    return new byte[0]
                }

                @Override
                void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {

                }

                @Override
                void verify(PublicKey key, String sigProvider) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {

                }

                @Override
                String toString() {
                    return null
                }

                @Override
                PublicKey getPublicKey() {
                    return null
                }

                @Override
                boolean hasUnsupportedCriticalExtension() {
                    return false
                }

                @Override
                Set<String> getCriticalExtensionOIDs() {
                    return null
                }

                @Override
                Set<String> getNonCriticalExtensionOIDs() {
                    return null
                }

                @Override
                byte[] getExtensionValue(String oid) {
                    return new byte[0]
                }
            }] as X509Certificate[]
            return _AcceptedIssuers
        }
    }
}
