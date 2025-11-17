-- Quick queries to check transactions in database

-- 1. Check if transactions table exists and count
SELECT COUNT(*) as total_transactions FROM transactions;

-- 2. Check transactions per user
SELECT user_id, COUNT(*) as transaction_count 
FROM transactions 
GROUP BY user_id
ORDER BY transaction_count DESC;

-- 3. View recent transactions for user 2
SELECT 
    id,
    transaction_id,
    description,
    amount,
    currency,
    timestamp,
    merchant_name,
    transaction_category,
    ingested_at
FROM transactions 
WHERE user_id = 2 
ORDER BY ingested_at DESC 
LIMIT 10;

-- 4. Check transaction date range
SELECT 
    MIN(timestamp) as earliest_transaction,
    MAX(timestamp) as latest_transaction,
    COUNT(*) as total_count
FROM transactions 
WHERE user_id = 2;
