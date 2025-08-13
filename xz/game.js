// 游戏常量
const EMPTY = 0;    // 空地
const WALL = 1;     // 墙壁
const BOX = 2;      // 箱子
const TARGET = 3;   // 目标点
const PLAYER = 4;   // 玩家
const BOX_ON_TARGET = 5;  // 箱子在目标点上
const PLAYER_ON_TARGET = 6;  // 玩家在目标点上

// 游戏变量
let canvas; 
let ctx; 
let gridSize = 40; 
let map = []; 
let player = {x: 0, y: 0}; 
let level = 1; 
let levels = []; 

// 初始化游戏
function initGame() {
    canvas = document.getElementById('gameCanvas');
    ctx = canvas.getContext('2d');

    // 定义关卡
    defineLevels();

    // 加载第一关
    loadLevel(level);

    // 绘制游戏
    draw();

    // 监听键盘事件
    document.addEventListener('keydown', handleKeyDown);

    // 监听按钮点击事件
    document.getElementById('up').addEventListener('click', () => movePlayer(0, -1));
    document.getElementById('down').addEventListener('click', () => movePlayer(0, 1));
    document.getElementById('left').addEventListener('click', () => movePlayer(-1, 0));
    document.getElementById('right').addEventListener('click', () => movePlayer(1, 0));
    document.getElementById('restart').addEventListener('click', () => loadLevel(level));
}

// 定义关卡
function defineLevels() {
    // 第一关
    levels[1] = [
        [1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
        [1, 0, 0, 0, 0, 0, 0, 0, 0, 1],
        [1, 0, 0, 0, 1, 1, 0, 0, 0, 1],
        [1, 0, 2, 0, 1, 3, 0, 0, 0, 1],
        [1, 0, 0, 0, 0, 3, 0, 0, 0, 1],
        [1, 0, 0, 2, 0, 1, 1, 0, 0, 1],
        [1, 0, 0, 0, 0, 1, 3, 0, 0, 1],
        [1, 0, 0, 0, 2, 0, 0, 0, 0, 1],
        [1, 0, 4, 0, 0, 0, 0, 0, 0, 1],
        [1, 1, 1, 1, 1, 1, 1, 1, 1, 1]
    ];

    // 第二关
    levels[2] = [
        [1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
        [1, 0, 0, 0, 0, 0, 0, 0, 0, 1],
        [1, 0, 0, 0, 1, 1, 1, 0, 0, 1],
        [1, 0, 0, 0, 1, 3, 1, 0, 0, 1],
        [1, 0, 1, 1, 1, 0, 1, 1, 0, 1],
        [1, 0, 1, 3, 0, 2, 0, 1, 0, 1],
        [1, 0, 1, 1, 1, 2, 1, 1, 0, 1],
        [1, 0, 0, 0, 0, 0, 0, 0, 0, 1],
        [1, 0, 0, 0, 1, 3, 1, 0, 4, 1],
        [1, 1, 1, 1, 1, 1, 1, 1, 1, 1]
    ];
}

// 加载关卡
function loadLevel(levelNum) {
    if (!levels[levelNum]) {
        alert('恭喜你通关了！');
        levelNum = 1;
    }

    level = levelNum;
    document.getElementById('level').textContent = `关卡: ${level}`;

    // 复制关卡地图
    map = JSON.parse(JSON.stringify(levels[level]));

    // 找到玩家位置
    for (let y = 0; y < map.length; y++) {
        for (let x = 0; x < map[y].length; x++) {
            if (map[y][x] === PLAYER || map[y][x] === PLAYER_ON_TARGET) {
                player.x = x;
                player.y = y;
            }
        }
    }
}

// 绘制游戏
function draw() {
    // 清空画布
    ctx.fillStyle = '#f0f0f0';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // 绘制地图
    for (let y = 0; y < map.length; y++) {
        for (let x = 0; x < map[y].length; x++) {
            const value = map[y][x];
            const posX = x * gridSize;
            const posY = y * gridSize;

            switch (value) {
                case WALL:
                    ctx.fillStyle = '#333';
                    ctx.fillRect(posX, posY, gridSize, gridSize);
                    break;
                case BOX:
                    ctx.fillStyle = '#8B4513';
                    ctx.fillRect(posX + 5, posY + 5, gridSize - 10, gridSize - 10);
                    break;
                case TARGET:
                    ctx.fillStyle = '#FFD700';
                    ctx.beginPath();
                    ctx.arc(posX + gridSize/2, posY + gridSize/2, gridSize/4, 0, Math.PI * 2);
                    ctx.fill();
                    break;
                case BOX_ON_TARGET:
                    // 先绘制目标点
                    ctx.fillStyle = '#FFD700';
                    ctx.beginPath();
                    ctx.arc(posX + gridSize/2, posY + gridSize/2, gridSize/4, 0, Math.PI * 2);
                    ctx.fill();
                    // 再绘制箱子
                    ctx.fillStyle = '#8B4513';
                    ctx.fillRect(posX + 5, posY + 5, gridSize - 10, gridSize - 10);
                    break;
                case PLAYER:
                    ctx.fillStyle = '#4CAF50';
                    ctx.beginPath();
                    ctx.arc(posX + gridSize/2, posY + gridSize/2, gridSize/3, 0, Math.PI * 2);
                    ctx.fill();
                    break;
                case PLAYER_ON_TARGET:
                    // 先绘制目标点
                    ctx.fillStyle = '#FFD700';
                    ctx.beginPath();
                    ctx.arc(posX + gridSize/2, posY + gridSize/2, gridSize/4, 0, Math.PI * 2);
                    ctx.fill();
                    // 再绘制玩家
                    ctx.fillStyle = '#4CAF50';
                    ctx.beginPath();
                    ctx.arc(posX + gridSize/2, posY + gridSize/2, gridSize/3, 0, Math.PI * 2);
                    ctx.fill();
                    break;
            }
        }
    }
}

// 处理键盘事件
function handleKeyDown(e) {
    switch (e.keyCode) {
        case 38: // 上
            movePlayer(0, -1);
            break;
        case 40: // 下
            movePlayer(0, 1);
            break;
        case 37: // 左
            movePlayer(-1, 0);
            break;
        case 39: // 右
            movePlayer(1, 0);
            break;
        case 82: // R键重新开始
            loadLevel(level);
            draw();
            break;
    }
}

// 移动玩家
function movePlayer(dx, dy) {
    const newX = player.x + dx;
    const newY = player.y + dy;

    // 检查是否超出边界
    if (newX < 0 || newX >= map[0].length || newY < 0 || newY >= map.length) {
        return;
    }

    const currentValue = map[player.y][player.x];
    const targetValue = map[newY][newX];

    // 尝试移动
    if (targetValue === EMPTY || targetValue === TARGET) {
        // 普通移动
        map[player.y][player.x] = currentValue === PLAYER ? EMPTY : TARGET;
        map[newY][newX] = targetValue === TARGET ? PLAYER_ON_TARGET : PLAYER;
        player.x = newX;
        player.y = newY;
    } else if (targetValue === BOX || targetValue === BOX_ON_TARGET) {
        // 推动箱子
        const boxNewX = newX + dx;
        const boxNewY = newY + dy;

        // 检查箱子移动后的位置
        if (boxNewX < 0 || boxNewX >= map[0].length || boxNewY < 0 || boxNewY >= map.length) {
            return;
        }

        const boxTargetValue = map[boxNewY][boxNewX];

        if (boxTargetValue === EMPTY || boxTargetValue === TARGET) {
            // 移动箱子
            map[boxNewY][boxNewX] = boxTargetValue === TARGET ? BOX_ON_TARGET : BOX;
            // 移动玩家
            map[player.y][player.x] = currentValue === PLAYER ? EMPTY : TARGET;
            map[newY][newX] = targetValue === BOX_ON_TARGET ? PLAYER_ON_TARGET : PLAYER;
            player.x = newX;
            player.y = newY;
        }
    }

    // 检查是否获胜
    if (checkWin()) {
        setTimeout(() => {
            alert('恭喜你完成关卡 ' + level + '！');
            loadLevel(level + 1);
        }, 500);
    }

    // 重绘游戏
    draw();
}

// 检查是否获胜
function checkWin() {
    for (let y = 0; y < map.length; y++) {
        for (let x = 0; x < map[y].length; x++) {
            if (map[y][x] === BOX || map[y][x] === TARGET) {
                // 如果还有未被推到目标点的箱子或未被覆盖的目标点，则未获胜
                if (map[y][x] === BOX) return false;
                // 检查是否有对应的箱子
                let hasBoxOnTarget = false;
                for (let y2 = 0; y2 < map.length; y2++) {
                    for (let x2 = 0; x2 < map[y2].length; x2++) {
                        if (map[y2][x2] === BOX_ON_TARGET) {
                            hasBoxOnTarget = true;
                            break;
                        }
                    }
                    if (hasBoxOnTarget) break;
                }
                if (!hasBoxOnTarget && map[y][x] === TARGET) return false;
            }
        }
    }
    return true;
}

// 启动游戏
window.onload = initGame;