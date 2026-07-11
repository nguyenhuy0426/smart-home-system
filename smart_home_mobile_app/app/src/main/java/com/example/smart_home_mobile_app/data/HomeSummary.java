package com.example.smart_home_mobile_app.data;

public final class HomeSummary {
    public final String homeId;
    public final String displayName;
    public final String role;

    public HomeSummary(String homeId, String displayName, String role) {
        this.homeId = homeId;
        this.displayName = displayName;
        this.role = role;
    }
}
