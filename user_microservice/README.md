🚀 User Microservice - Keycloak Admin Client
📋 Overview
Spring Boot microservice for managing Keycloak users through administrative API. 
This service provides secure access to Keycloak user management operations using client credentials grant.
⚠️ Important

This microservice runs on port 8083

All external access goes through the API Gateway on port 8888

🚀 Quick Start
Prerequisites
Java 17+ (OpenJDK 17 or higher recommended)

Maven 3.8+ or Gradle 7+

Keycloak 25.0+ running on port 8090

Node.js 16+ & Angular CLI (for frontend)

Docker (optional, for containerized Keycloak)

Installation
Clone and build the microservice:

bash
# Clone the repository
git clone <repository-url>
cd user_microservice

# Build the project
mvn clean install
# OR with Gradle
gradle build
Run the user-microservice

bash
# Using Maven
mvn spring-boot:run

# Using Java
java -jar target/user-microservice-1.0.0.jar
The service will start on: http://localhost:8083

🌐 API Gateway

The API Gateway runs separately and exposes backend services.

Gateway Port: 8888

User Microservice Route: /api/users

Example access path:

http://localhost:8888/api/users

🔐 Keycloak Configuration
Step 1: Start Keycloak
Option A: Docker (Recommended)
bash
docker run -d \
--name keycloak \
-p 8090:8080 \
-e KEYCLOAK_ADMIN=admin \
-e KEYCLOAK_ADMIN_PASSWORD=admin \
quay.io/keycloak/keycloak:25.0.0 \
start-dev
Option B: Local Installation
bash
# Download and start Keycloak
wget https://github.com/keycloak/keycloak/releases/download/25.0.0/keycloak-25.0.0.zip
unzip keycloak-25.0.0.zip
cd keycloak-25.0.0/bin
./kc.sh start-dev --http-port=8090
Step 2: Configure Keycloak Realm
Access Keycloak Admin Console:

URL: http://localhost:8090

Login: admin / admin

Create Realm:

Click "Create realm"

Name: coral-io_realm

Click "Create"

Create Admin Client:

Navigate to Clients → Create client

Client ID: admin-client

Client type: OpenID Connect

Click "Next"

✅ Enable Client authentication

Click "Save"

Configure Client Credentials:

yaml
⚠️ IMPORTANT: Update these values in your KeycloakAdminClient.java

Current configuration in code:
- Client ID: admin-client
- Client Secret: vov2JMbDjbDsZQCyJeBZIqcH2W5blsdD

To get/set these in Keycloak:
1. Go to your client → Credentials tab
2. Copy the "Client secret"
3. Update the CLIENT_SECRET in KeycloakAdminClient.java
   Assign Required Roles:

Go to Clients → admin-client → Service Account Roles

Under "Realm roles", assign:

ADMIN role 

Under "Client roles", assign:

manage-users (realm-management)

view-users (realm-management)

Step 3: Verify Configuration
bash
# Test the connection
curl -X GET http://localhost:8888/api/users \
-H "Content-Type: application/json"
⚙️ Application Configuration
Backend Configuration
Update your KeycloakAdminClient.java with your actual Keycloak credentials:

java
// Ensure these match your Keycloak setup
private static final String SERVER_URL = "http://localhost:8090";
private static final String REALM = "coral-io_realm";
private static final String CLIENT_ID = "admin-client";
private static final String CLIENT_SECRET = "vov2JMbDjbDsZQCyJeBZIqcH2W5blsdD"; // ⚠️ Update this!
Frontend Configuration
Your Angular service should connect to the microservice:

typescript
// Update this base URL if your microservice runs on a different port
private baseUrl = 'http://localhost:8888/api/users';

📖 API Reference
Available Endpoints
Method	Endpoint	Description	Required Role
GET	/api/users	Get all users	User with token
GET	/api/users/{id}	Get user by ID	User with token
GET	/api/users/keycloak	Get raw Keycloak users	User with token

4. Access Points
   Angular App: http://localhost:4200

User Microservice: http://localhost:8888

Keycloak Admin: http://localhost:8090

🧪 Testing the Integration
Test Script
bash
# 1. Get a token from Keycloak (simulate frontend login)
TOKEN=$(curl -X POST http://localhost:8090/realms/coral-io_realm/protocol/openid-connect/token \
-H "Content-Type: application/x-www-form-urlencoded" \
-d "username=admin&password=admin123&grant_type=password&client_id=expense-app" \
| jq -r '.access_token')

# 2. Call the microservice with the token
curl -X GET http://localhost:8888/api/users \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json"

Expected Results
✅ 200 OK with user list: Integration working

❌ 401 Unauthorized: Token invalid or expired

❌ 403 Forbidden: Missing required roles

❌ 500 Internal Error: Keycloak connection issues

📚 Additional Resources
Keycloak Admin REST API

Keycloak Java Admin Client

Spring Boot Security

Angular HTTP Client

🆘 Support
For issues and questions:

Check the troubleshooting section

Review Keycloak server logs

Verify configuration steps

Contact development team

📄 License
This project is licensed under the MIT License - see the LICENSE file for details.

🤝 Contributing
Fork the repository

Create a feature branch

Commit changes

Push to branch

Open a Pull Request

