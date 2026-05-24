<#ftl strip_whitespace=true>
<#import "account.ftl" as layout>
<@layout.accountLayout>
    <div class="welcome-section">
        <h2>Welcome to Your Account</h2>
        <p>Manage your personal information, security settings, and connected applications.</p>
    </div>
    
    <div class="account-cards">
        <div class="card">
            <div class="card-icon">
                <i class="fas fa-user-edit"></i>
            </div>
            <h3>Personal Information</h3>
            <p>Update your name, email, and other personal details</p>
            <a href="${url.accountUrl}" class="card-link">View Profile</a>
        </div>
        
        <div class="card">
            <div class="card-icon">
                <i class="fas fa-shield-alt"></i>
            </div>
            <h3>Security</h3>
            <p>Change your password and manage security settings</p>
            <a href="${url.accountUrl}/password" class="card-link">Security Settings</a>
        </div>
        
        <div class="card">
            <div class="card-icon">
                <i class="fas fa-laptop"></i>
            </div>
            <h3>Sessions</h3>
            <p>Manage your active login sessions and devices</p>
            <a href="${url.accountUrl}/sessions" class="card-link">View Sessions</a>
        </div>
        
        <div class="card">
            <div class="card-icon">
                <i class="fas fa-th"></i>
            </div>
            <h3>Applications</h3>
            <p>Manage applications connected to your account</p>
            <a href="${url.accountUrl}/applications" class="card-link">Applications</a>
        </div>
    </div>
</@layout.accountLayout>