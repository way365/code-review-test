// 获取Canvas元素和上下文
const canvas = document.getElementById('clockCanvas');
const ctx = canvas.getContext('2d');
const digitalClock = document.getElementById('digitalClock');

// 设置Canvas的宽高为相同值，确保钟表是圆形的
const clockSize = Math.min(canvas.width, canvas.height);
const centerX = canvas.width / 2;
const centerY = canvas.height / 2;
const radius = clockSize * 0.45;

// 初始化钟表
function initClock() {
    // 绘制钟表
    drawClock();
    // 更新时间
    updateTime();
    // 设置定时器，每秒更新一次
    setInterval(updateTime, 1000);
}

// 绘制钟表
function drawClock() {
    // 清空画布
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // 绘制表盘
    drawFace();
    // 绘制刻度
    drawTicks();
    // 绘制数字
    drawNumbers();
}

// 绘制表盘
function drawFace() {
    // 外圆
    ctx.beginPath();
    ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
    ctx.fillStyle = '#fff';
    ctx.fill();
    ctx.strokeStyle = '#333';
    ctx.lineWidth = 4;
    ctx.stroke();

    // 内圆
    ctx.beginPath();
    ctx.arc(centerX, centerY, radius * 0.05, 0, Math.PI * 2);
    ctx.fillStyle = '#333';
    ctx.fill();
}

// 绘制刻度
function drawTicks() {
    ctx.lineWidth = 2;

    for (let i = 0; i < 12; i++) {
        const angle = (i / 12) * Math.PI * 2;
        const startX = centerX + Math.cos(angle) * (radius * 0.9);
        const startY = centerY + Math.sin(angle) * (radius * 0.9);
        const endX = centerX + Math.cos(angle) * radius;
        const endY = centerY + Math.sin(angle) * radius;

        ctx.beginPath();
        ctx.moveTo(startX, startY);
        ctx.lineTo(endX, endY);
        ctx.stroke();
    }

    // 绘制分钟刻度
    ctx.lineWidth = 1;

    for (let i = 0; i < 60; i++) {
        if (i % 5 !== 0) { // 跳过小时刻度的位置
            const angle = (i / 60) * Math.PI * 2;
            const startX = centerX + Math.cos(angle) * (radius * 0.95);
            const startY = centerY + Math.sin(angle) * (radius * 0.95);
            const endX = centerX + Math.cos(angle) * radius;
            const endY = centerY + Math.sin(angle) * radius;

            ctx.beginPath();
            ctx.moveTo(startX, startY);
            ctx.lineTo(endX, endY);
            ctx.stroke();
        }
    }
}

// 绘制数字
function drawNumbers() {
    ctx.font = 'bold 20px Arial';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = '#333';

    for (let i = 1; i <= 12; i++) {
        const angle = (i / 12) * Math.PI * 2;
        const x = centerX + Math.cos(angle) * (radius * 0.8);
        const y = centerY + Math.sin(angle) * (radius * 0.8);

        ctx.fillText(i.toString(), x, y);
    }
}

// 绘制指针
function drawHands(hour, minute, second) {
    // 绘制时针
    const hourAngle = ((hour % 12) / 12) * Math.PI * 2 + (minute / 60) * (Math.PI * 2 / 12);
    drawHand(hourAngle, radius * 0.5, 6, '#333');

    // 绘制分针
    const minuteAngle = (minute / 60) * Math.PI * 2 + (second / 60) * (Math.PI * 2 / 60);
    drawHand(minuteAngle, radius * 0.7, 4, '#555');

    // 绘制秒针
    const secondAngle = (second / 60) * Math.PI * 2;
    drawHand(secondAngle, radius * 0.85, 2, '#f00');
}

// 绘制单个指针
function drawHand(angle, length, width, color) {
    ctx.beginPath();
    ctx.moveTo(centerX, centerY);
    ctx.lineTo(
        centerX + Math.cos(angle - Math.PI/2) * length,
        centerY + Math.sin(angle - Math.PI/2) * length
    );
    ctx.lineWidth = width;
    ctx.strokeStyle = color;
    ctx.lineCap = 'round';
    ctx.stroke();
}

// 更新时间
function updateTime() {
    const now = new Date();
    const hour = now.getHours();
    const minute = now.getMinutes();
    const second = now.getSeconds();

    // 绘制钟表
    drawClock();
    // 绘制指针
    drawHands(hour, minute, second);

    // 更新数字时钟
    const timeString = now.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    });
    digitalClock.textContent = timeString;
}

// 启动钟表
window.onload = initClock;