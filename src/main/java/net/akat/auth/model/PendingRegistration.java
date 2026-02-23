package net.akat.auth.model;

import java.time.Instant;

public class PendingRegistration {
    private final String nickname;
    private final String email;
    private final String ipAddress;
    private final Instant createdAt;
    private final Instant expiresAt;

    private PendingRegistration(Builder builder) {
        this.nickname = builder.nickname;
        this.email = builder.email;
        this.ipAddress = builder.ipAddress;
        this.createdAt = builder.createdAt;
        this.expiresAt = builder.expiresAt;
    }

    public static class Builder {
        private String nickname;
        private String email;
        private String ipAddress;
        private Instant createdAt = Instant.now();
        private Instant expiresAt;

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder withTimeoutSeconds(int seconds) {
            this.expiresAt = this.createdAt.plusSeconds(seconds);
            return this;
        }

        public PendingRegistration build() {
            return new PendingRegistration(this);
        }
    }

    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
