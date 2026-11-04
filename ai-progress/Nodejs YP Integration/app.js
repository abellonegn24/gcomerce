const express = require('express');

const session = require('express-session');
const bodyParser = require('body-parser');
const crypto = require('crypto');
const path = require('path');

const app = express();
app.use(express.static(path.join(__dirname, 'public')));

// Config
const DEFAULT_MERCHANT_ID = "202507070002";
const DEFAULT_MERCHANT_KEY = "rCpRQKCH8E+Wp7RpKXYAkNM+rcsS3LW71NrdAWRdwLQ=";
const AGGREGATOR_ID = "yagout";
const SUCCESS_URL = "http://localhost:3000/success";
const FAILURE_URL = "http://localhost:5000/failure";
const TEST_URL = "https://checkout.yagoutpay.com/ms-transaction-core-1-0/paymentRedirection/checksumGatewayPage";
const CURRENCY_FROM = "ETH";
const CURRENCY_TO = "ETB";

// App setup
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));
app.use(bodyParser.urlencoded({ extended: true }));
app.use(session({
  secret: 'your_secret_key',
  resave: false,
  saveUninitialized: true
}));

// Helper encryption/decryption
const IV = Buffer.from("0123456789abcdef", "utf-8"); 

function getAesAlgorithm(keyBuffer) {
    const len = keyBuffer.length;
    if (len === 16) return 'aes-128-cbc';
    if (len === 24) return 'aes-192-cbc';
    if (len === 32) return 'aes-256-cbc';
    throw new Error("Invalid key length. Must be 16, 24, or 32 bytes.");
}

function encrypt(text, base64Key) {
    const key = Buffer.from(base64Key, 'base64');
    const algorithm = getAesAlgorithm(key);
    const cipher = crypto.createCipheriv(algorithm, key, IV);
    let encrypted = cipher.update(text, 'utf8', 'base64');
    encrypted += cipher.final('base64');
    return encrypted;
}

function decrypt(encryptedBase64, base64Key) {
  const key = Buffer.from(base64Key, 'base64');
  const algorithm = getAesAlgorithm(key);           
  const decipher = crypto.createDecipheriv(algorithm, key, IV);

  let decrypted = decipher.update(encryptedBase64, 'base64', 'utf8');
  decrypted += decipher.final('utf8');
  return decrypted;
}


function generateSha256Hash(input) {
  return crypto.createHash('sha256').update(input, 'utf8').digest('hex');
}

// Routes
app.get('/', (req, res) => {
  res.redirect('/login');
});

app.get('/login', (req, res) => {
  res.render('login', { error: null });
});

app.post('/login', (req, res) => {
  const { username, password } = req.body;
  if (username === 'admin' && password === '1234') {
    req.session.loggedIn = true;
    res.redirect('/transaction');
  } else {
    res.render('login', { error: 'Invalid credentials' });
  }
});

app.get('/transaction', (req, res) => {
  if (!req.session.loggedIn) return res.redirect('/login');
  res.render('transaction');
});

app.post('/transaction', (req, res) => {
  if (!req.session.loggedIn) return res.redirect('/login');

  const submittedMerchantId = DEFAULT_MERCHANT_ID;
  const submittedMerchantKey = DEFAULT_MERCHANT_KEY;

  const amount = req.body.amount || '100';
  const mobileNumber = req.body.mobile_number || '';
  let orderNumber = req.body.order_number;
  if (!orderNumber) {
    orderNumber = "ORDER_" + crypto.randomBytes(4).toString('hex');
    // orderNumber = "ORDER_1400e539"
  }

  if (submittedMerchantId !== DEFAULT_MERCHANT_ID || submittedMerchantKey !== DEFAULT_MERCHANT_KEY) {
    return res.render('payment_failure', { error: "Invalid Merchant ID or Key. Please try again." });
  }

  const txnDetails = `${AGGREGATOR_ID}|${submittedMerchantId}|${orderNumber}|${amount}|ETH|ETB|SALE|${SUCCESS_URL}|${FAILURE_URL}|WEB`;
  const custDetails = `||${mobileNumber}||Y`; 
  const fullMessage = [
    txnDetails,
    "|||",
    "||||",
    custDetails,
    "||||",
    "||||||",
    "||",
    "",
    "||||"
  ].join("~");

   console.log(`Full Message: ${fullMessage}`);

  const encryptedData = encrypt(fullMessage, DEFAULT_MERCHANT_KEY);
  const hashInput = `${submittedMerchantId}~${orderNumber}~${amount}~${CURRENCY_FROM}~${CURRENCY_TO}`;
  const encryptedHash = encrypt(generateSha256Hash(hashInput), DEFAULT_MERCHANT_KEY);



    console.log("DEFAULT_MERCHANT_KEY:", DEFAULT_MERCHANT_KEY);
    console.log("submittedMerchantId:", submittedMerchantId);
    console.log("orderNumber:", orderNumber);
    console.log("amount:", amount);
    console.log("CURRENCY_FROM:", CURRENCY_FROM);
    console.log("CURRENCY_TO:", CURRENCY_TO);


    console.log("Encrypted Data:", encryptedData);
    console.log("hash" , generateSha256Hash(hashInput));
    console.log("Encrypted Hash:", encryptedHash);
    console.log("Hash input:", hashInput);

  res.render('redirect', {test_url: TEST_URL,me_id: submittedMerchantId,encrypted_data: encryptedData,encrypted_hash: encryptedHash});
});

app.post('/success', (req, res) => {
  const txnResponse = req.body.txn_response;
  if (!txnResponse) {
    return res.render('payment_failure', { error: "No transaction response received." });
  }

  try {
    const decryptedResponse = decrypt(txnResponse, DEFAULT_MERCHANT_KEY);
    console.log("Decrypted Response (RAW):", decryptedResponse);

    // Pass the raw response string into template
    const data = {
      rawResponse: decryptedResponse
    };

    res.render('payment_result', data);

  } catch (err) {
    res.render('payment_failure', { error: `Decryption failed: ${err.message}` });
  }
});



app.post('/failure', (req, res) => {
  const txnResponse = req.body.txn_response;
  if (!txnResponse) {
    return res.render('payment_failure', { error: "No transaction response received." });
  }

  try {
    const decryptedResponse = decrypt(txnResponse, DEFAULT_MERCHANT_KEY);
    res.render('payment_failure', { response: decryptedResponse });
  } catch (err) {
    res.render('payment_failure', { error: `Decryption failed: ${err.message}` });
  }
});

// Start server
const PORT = 3000;
app.listen(PORT, () => console.log(`Server running on http://localhost:${PORT}`));