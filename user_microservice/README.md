🔐 GLOBAL KEYCLOAK CONFIGURATION – CORALIO

Realm: coral-io_realm
Keycloak URL: http://localhost:8090

🧱 Architecture Overview

You correctly separated:

🔹 expense-app → Login client (Frontend authentication)

🔹 admin-client → Backend admin operations (User microservice)

This is the correct microservice architecture.

1️⃣ expense-app (Frontend Authentication Client)

Used by:

Angular app (http://localhost:4200
)

Purpose:

Authenticate users

Issue JWT

Include departmentId claim

✅ Correct Configuration for expense-app
General

Client ID: expense-app

Enabled: ✅

🔐 Access Settings
Setting	Value
Root URL	http://localhost:4200

Home URL	http://localhost:4200

Valid Redirect URIs	http://localhost:4200/
*
Valid Post Logout URIs	http://localhost:4200/
*
Web origins = *

⚙️ Capability Config
Setting	Value
Client Authentication	❌ OFF
Standard Flow	✅ ON
Direct Access Grants	❌ OFF (enable only for testing)
Implicit Flow	❌ OFF
Service Accounts	❌ OFF
Authorization	❌ OFF

This is correct for a public SPA (Angular app).

🔐 PKCE (Recommended)

Set:

Setting	Value
PKCE Method	S256

2️⃣ departmentId Token Configuration

Final clean setup:

Step 1 – Create Client Scope

Client Scope name: department

Step 2 – Add Mapper

Mapper Type: User Attribute

Setting	Value
User Attribute	departmentId
Token Claim Name	departmentId
Claim JSON Type	String
Add to Access Token	✅ ON
Add to ID Token	✅ ON
Add to UserInfo	✅ ON
Multivalued	❌ OFF
Step 3 – Attach Scope

Go to:

Clients → expense-app → Client Scopes

Add department as:

Default Client Scope ✅

Now every login token contains:

{
"preferred_username": "ghofrane",
"departmentId": "3",
"realm_access": {
"roles": ["EMPLOYEE"]
}
}

Perfect implementation 👌

3️⃣ admin-client (Backend Client)

Used by:

USER-MICROSERVICE (port 8083)

Grant Type:

client_credentials
✅ Correct admin-client Configuration
General

Client ID: admin-client

Client Authentication: ✅ ON

Authorization: ❌ OFF

Standard Flow: ❌ OFF

Direct Access Grants: ❌ OFF

Service Accounts Enabled: ✅ ON

🔐 Service Account Roles

Assign ONLY:

From realm-management:

manage-users

view-users

manage-realm

ADMIN (from expense-app realm)