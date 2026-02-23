package net.akat.auth.config;

import com.google.inject.Singleton;

@Singleton
public class PluginConfig {
    private final int registrationTimeoutSeconds = 300;
    private final int apiPort = 8668;
    private final String apiKey = "xQ9!pL#2@kZ$8%mY^1&nX*3(oW)5_rV+7=tU-0[qT]9{pR}7|sQ6aP5;bO4:cN3,dM2.eL1/fK0?gJ>9<hI8>iG7>jF6>kE5>lD4>mC3>nB2>oA1>p@0>q#9>r$8>s%7>t^6>u&5>v*4>w(3>x)2>y_1>z+0";
    private final boolean strictIpCheck = true;

    private final String websiteUrl = "http://92.53.99.85:8998";
    private final String websiteApiPath = "/internal/players/account/approve";
    private final String websiteApiKey = "730222ffe0b86a26e0a6d0a6055fc99520b20143fc01c3b69e80b4030f09df54a072892381846863b8393c95bbbbfea52a8357b80eb228d4da8f729388fe6e85";
    private final int websiteTimeoutSeconds = 10;
    private final String websiteNewIpPath = "/internal/players/verify";

    public int getRegistrationTimeoutSeconds() { return registrationTimeoutSeconds; }
    public int getApiPort() { return apiPort; }
    public String getApiKey() { return apiKey; }
    public boolean isStrictIpCheck() { return strictIpCheck; }

    public String getWebsiteUrl() { return websiteUrl; }
    public String getWebsiteApiPath() { return websiteApiPath; }
    public String getWebsiteApiKey() { return websiteApiKey; }
    public int getWebsiteTimeoutSeconds() { return websiteTimeoutSeconds; }
    public String getWebsiteApprovalUrl() {
        return websiteUrl + websiteApiPath;
    }

    public String getWebsiteNewIpPath() { return websiteNewIpPath; }
    public String getWebsiteNewIpUrl() {
        return websiteUrl + websiteNewIpPath;
    }
}
