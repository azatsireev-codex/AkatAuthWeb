package net.akat.auth.dto;

public class RegistrationRequest {
    private String ipAddress;
    private String nickname;
    private String email;

    public RegistrationRequest() {
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIpAddress() { return ipAddress; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
}
