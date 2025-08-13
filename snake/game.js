const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');
const scoreElement = document.getElementById('score');

// 游戏配置
const gridSize = 20;
const tileCount = canvas.width / gridSize;
let snake = [ { x: 10, y: 10 } ];
let food = { x: 15, y: 15 };
let dx = 0;
let dy = 0;
let score = 0;
let gameSpeed = 150;

// 初始化游戏
function startGame() {
    // 监听键盘事件
    document.addEventListener('keydown', changeDirection);
    // 初始方向向右
    dx = gridSize;
    // 启动游戏循环
    setInterval(update, gameSpeed);
}

// 更新游戏状态
function update() {
    // 移动蛇头
    const head = { x: snake[0].x + dx, y: snake[0].y + dy };
    snake.unshift(head);

    // 检查是否吃到食物
    if (head.x === food.x && head.y === food.y) {
        score += 10;
        scoreElement.textContent = `得分：${score}`;
        generateFood();
    } else {
        snake.pop();
    }

    // 碰撞检测（墙或自身）
    if (checkCollision(head) || isOutOfBounds(head)) {
        alert('游戏结束！最终得分：' + score);
        location.reload();
    }

    // 绘制画面
    draw();
}

// 绘制所有元素
function draw() {
    // 清空画布
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // 绘制蛇
    ctx.fillStyle = '#00ff00';
    snake.forEach(segment => {
        ctx.fillRect(segment.x, segment.y, gridSize - 2, gridSize - 2);
    });

    // 绘制食物
    ctx.fillStyle = '#ff0000';
    ctx.fillRect(food.x, food.y, gridSize - 2, gridSize - 2);
}

// 改变方向（限制不能反向移动）
function changeDirection(event) {
    const LEFT_KEY = 37;
    const RIGHT_KEY = 39;
    const UP_KEY = 38;
    const DOWN_KEY = 40;
    const keyPressed = event.keyCode;
    const goingUp = dy === -gridSize;
    const goingDown = dy === gridSize;
    const goingRight = dx === gridSize;
    const goingLeft = dx === -gridSize;

    if (keyPressed === LEFT_KEY && !goingRight) {
        dx = -gridSize;
        dy = 0;
    }
    if (keyPressed === UP_KEY && !goingDown) {
        dx = 0;
        dy = -gridSize;
    }
    if (keyPressed === RIGHT_KEY && !goingLeft) {
        dx = gridSize;
        dy = 0;
    }
    if (keyPressed === DOWN_KEY && !goingUp) {
        dx = 0;
        dy = gridSize;
    }
}

// 生成随机食物位置
function generateFood() {
    food = {
        x: Math.floor(Math.random() * tileCount) * gridSize,
        y: Math.floor(Math.random() * tileCount) * gridSize
    };
    // 避免食物生成在蛇身上
    snake.forEach(segment => {
        if (food.x === segment.x && food.y === segment.y) {
            generateFood();
        }
    });
}

// 检查是否碰撞自身
function checkCollision(head) {
    return snake.slice(1).some(segment => segment.x === head.x && segment.y === head.y);
}

// 检查是否出界
function isOutOfBounds(head) {
    return head.x < 0 || head.x >= canvas.width || head.y < 0 || head.y >= canvas.height;
}

// 启动游戏
startGame();