package net.akat.auth.dto;

public class VerificationRequest {
    private String nickname;
    private String ipAddress;

    public VerificationRequest() {}

    public VerificationRequest(String nickname, String ipAddress) {
        this.nickname = nickname;
        this.ipAddress = ipAddress;
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
