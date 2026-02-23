package net.akat.auth.model;

import java.time.Instant;

public class Player {
    private final String nickname;
    private final String email;
    private final String originalIp;
    private final Instant registrationDate;
    private String lastIp;
    private Instant lastLogin;
    private boolean active;

    private Player(Builder builder) {
        this.nickname = builder.nickname;
        this.email = builder.email;
        this.originalIp = builder.originalIp;
        this.registrationDate = builder.registrationDate;
        this.lastIp = builder.lastIp;
        this.lastLogin = builder.lastLogin;
        this.active = builder.active;
    }

    public static class Builder {
        private String nickname;
        private String email;
        private String originalIp;
        private Instant registrationDate = Instant.now();
        private String lastIp;
        private Instant lastLogin;
        private boolean active = true;

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder originalIp(String originalIp) {
            this.originalIp = originalIp;
            return this;
        }

        public Builder registrationDate(Instant registrationDate) {
            this.registrationDate = registrationDate;
            return this;
        }

        public Builder lastIp(String lastIp) {
            this.lastIp = lastIp;
            return this;
        }

        public Builder lastLogin(Instant lastLogin) {
            this.lastLogin = lastLogin;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Player build() {
            return new Player(this);
        }
    }

    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public String getOriginalIp() { return originalIp; }
    public Instant getRegistrationDate() { return registrationDate; }
    public String getLastIp() { return lastIp; }
    public Instant getLastLogin() { return lastLogin; }
    public boolean isActive() { return active; }
}
