package com.example.smart_home_mobile_app.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

public final class FirebaseServices {
    public final FirebaseAuth auth;
    public final FirebaseDatabase database;

    public FirebaseServices(FirebaseAuth auth, FirebaseDatabase database) {
        this.auth = auth;
        this.database = database;
    }
}
