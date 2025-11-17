# Setup Guide

## Quick Start

1. **Copy the example configuration file:**
   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application.properties
   ```

2. **Update `application.properties` with your credentials:**
   - Database connection details
   - TrueLayer client ID and secret
   - LLM API key (Gemini or OpenAI)
   - JWT secret (generate with: `openssl rand -base64 32`)

3. **Or use environment variables (recommended for production):**
   ```bash
   export DB_HOST=your_db_host
   export DB_PASSWORD=your_db_password
   export TRUELAYER_CLIENT_ID=your_client_id
   export TRUELAYER_CLIENT_SECRET=your_client_secret
   export LLM_API_KEY=your_llm_api_key
   export JWT_SECRET=your_jwt_secret
   ```

## Environment Variables

The application supports the following environment variables:

### Database
- `DB_HOST` - Database host (default: localhost)
- `DB_PORT` - Database port (default: 5432)
- `DB_NAME` - Database name (default: truelayer_app)
- `DB_USERNAME` - Database username (default: postgres)
- `DB_PASSWORD` - Database password (required)
- `DB_SSL_MODE` - SSL mode (default: prefer)

### Authentication
- `JWT_SECRET` - JWT signing secret (required, min 32 chars)

### TrueLayer
- `TRUELAYER_CLIENT_ID` - TrueLayer client ID (required)
- `TRUELAYER_CLIENT_SECRET` - TrueLayer client secret (required)
- `TRUELAYER_REDIRECT_URI` - OAuth redirect URI (default: http://localhost:8080/callback)

### LLM
- `LLM_PROVIDER` - LLM provider: `gemini` or `openai` (default: gemini)
- `LLM_API_KEY` - API key for your chosen LLM provider (required)

## Local Development

For local development, you can either:

1. **Use environment variables** (recommended)
2. **Edit `application.properties` directly** (not recommended for production)

The `application.properties` file is gitignored to prevent committing secrets.

## Production Deployment

**Always use environment variables in production!** Never commit secrets to version control.

Set all required environment variables in your deployment platform:
- Google Cloud Run: Use Secret Manager or environment variables
- AWS: Use Secrets Manager or environment variables
- Heroku: Use Config Vars
- Docker: Use environment variables or secrets

## Security Notes

- ✅ `application.properties.example` is safe to commit (contains placeholders only)
- ✅ `application.properties` is gitignored (contains actual secrets)
- ✅ All sensitive values use environment variable fallbacks
- ⚠️ Never commit actual API keys, passwords, or secrets
- ⚠️ Rotate secrets regularly in production

