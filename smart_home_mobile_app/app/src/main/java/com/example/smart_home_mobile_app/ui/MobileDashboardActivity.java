package com.example.smart_home_mobile_app.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.example.smart_home_mobile_app.R;
import com.example.smart_home_mobile_app.ui.screens.AccountFragment;
import com.example.smart_home_mobile_app.ui.screens.CameraFragment;
import com.example.smart_home_mobile_app.ui.screens.CreateHomeFragment;
import com.example.smart_home_mobile_app.ui.screens.JoinHomeFragment;
import com.example.smart_home_mobile_app.ui.screens.LoginFragment;
import com.example.smart_home_mobile_app.ui.screens.ManageHomesFragment;
import com.example.smart_home_mobile_app.ui.screens.MainFragment;
import com.example.smart_home_mobile_app.ui.screens.NodeDetailsFragment;
import com.example.smart_home_mobile_app.ui.screens.RoomDetailFragment;
import com.example.smart_home_mobile_app.ui.screens.RoomsFragment;

public class MobileDashboardActivity extends AppCompatActivity
        implements SmartHomeAppController.Listener {

    private SmartHomeAppController controller;
    private BottomNavigationView bottomNav;
    private View bottomNavContainer;
    private Boolean showingMain;
    private boolean updatingBottomNav;

    public SmartHomeAppController getController() {
        return controller;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controller = new SmartHomeAppController(getApplicationContext());
        setContentView(R.layout.activity_dashboard);
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNavContainer = findViewById(R.id.bottom_nav_container);
        setupBottomNav();
        controller.addListener(this);
        syncRootFragment();
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            if (updatingBottomNav) return true;
            int itemId = item.getItemId();
            if (itemId == R.id.tab_overview) return showTopLevelTab(new MainFragment(), itemId);
            if (itemId == R.id.tab_rooms) return showTopLevelTab(new RoomsFragment(), itemId);
            if (itemId == R.id.tab_account) return showTopLevelTab(new AccountFragment(), itemId);
            return false;
        });
    }

    @Override
    public void onControllerStateChanged() {
        syncRootFragment();
    }

    /** Switches between the login screen and the signed-in shell on auth transitions. */
    private void syncRootFragment() {
        boolean shouldShowMain =
                controller.authState().status == AuthStatus.SIGNED_IN || controller.isPreviewMode();
        if (bottomNavContainer != null) {
            bottomNavContainer.setVisibility(shouldShowMain ? View.VISIBLE : View.GONE);
        }
        if (showingMain != null && showingMain == shouldShowMain) {
            return;
        }
        showingMain = shouldShowMain;
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        if (shouldShowMain) {
            showTopLevelTab(new MainFragment(), R.id.tab_overview);
        } else {
            fm.beginTransaction().replace(R.id.nav_host, new LoginFragment()).commit();
        }
    }

    private boolean showTopLevelTab(Fragment fragment, int itemId) {
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fm.beginTransaction().replace(R.id.nav_host, fragment).commit();
        if (bottomNav != null && bottomNav.getSelectedItemId() != itemId) {
            updatingBottomNav = true;
            bottomNav.setSelectedItemId(itemId);
            updatingBottomNav = false;
        }
        return true;
    }

    public void navigateToNodeDetails(String nodeId) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host, NodeDetailsFragment.newInstance(nodeId))
                .addToBackStack("node_details")
                .commit();
    }

    public void navigateToCamera() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host, new CameraFragment())
                .addToBackStack("camera")
                .commit();
    }

    public void navigateToRoomDetail(String roomId) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host, RoomDetailFragment.newInstance(roomId))
                .addToBackStack("room_detail")
                .commit();
    }

    public void navigateToAccount() {
        showTopLevelTab(new AccountFragment(), R.id.tab_account);
    }

    public void navigateToManageHomes() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host, new ManageHomesFragment())
                .addToBackStack("manage_homes")
                .commit();
    }

    public void navigateToJoinHome() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host, new JoinHomeFragment())
                .addToBackStack("join_home")
                .commit();
    }

    public void navigateToCreateHome() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host, new CreateHomeFragment())
                .addToBackStack("create_home")
                .commit();
    }

    public void returnToMain() {
        showingMain = true;
        showTopLevelTab(new MainFragment(), R.id.tab_overview);
    }

    @Override
    protected void onDestroy() {
        controller.removeListener(this);
        controller.close();
        super.onDestroy();
    }
}
