document.addEventListener('DOMContentLoaded', function() {
    // 动态加载自行车SVG
    loadBikeSVG();

    // 为自行车类型卡片添加交互
    setupBikeTypeCards();

    // 为自行车部件添加交互
    setupPartItems();

    // 平滑滚动
    setupSmoothScroll();

    // 启动车轮旋转动画
    startWheelAnimation();
});

// 车轮旋转动画
function startWheelAnimation() {
    const svgContainer = docunt.querySelector('.bike-svg-container');
    const frontWheel = svgContainer.querySelector('circle[cx="400"][cy="200"][r="50"]');
    const rearWheel = svgContainer.querySelector('circle[cx="150"][cy="200"][r="50"]');
    let rotation = 0;

    // 创建旋转动画
    function animate() {
        rotation += 2; // 旋转速度
        if (rotation >= 360) rotation = 0;

        // 应用旋转变换
        if (frontWheel && rearWheel) {
            frontWheel.setAttribute('transform', `rotate(${rotation} 400 200)`);
            rearWheel.setAttribute('transform', `rotate(${rotation} 150 200)`);
        }

        requestAnimationFrame(animate);
    }

    animate();
}

// 动态加载自行车SVG
function loadBikeSVG() {
    const svgContainer = document.querySelector('.bike-svg-container');

    // 创建一个基本的自行车SVG
    const bikeSVG = `
    <svg width="500" height="250" viewBox="0 0 500 250" xmlns="http://www.w3.org/2000/svg">
        <!-- 车架 -->
        <path d="M100,200 L150,150 L250,150 L300,100 L350,150 L400,150" stroke="#333" stroke-width="8" fill="none" />
        <path d="M150,150 L150,100 L250,100 L250,150" stroke="#333" stroke-width="8" fill="none" />
        <path d="M250,100 L300,100" stroke="#333" stroke-width="8" fill="none" />

        <!-- 车轮 -->
        <circle cx="150" cy="200" r="50" stroke="#333" stroke-width="5" fill="none" />
        <circle cx="150" cy="200" r="40" stroke="#333" stroke-width="2" fill="none" />
        <circle cx="400" cy="200" r="50" stroke="#333" stroke-width="5" fill="none" />
        <circle cx="400" cy="200" r="40" stroke="#333" stroke-width="2" fill="none" />

        <!-- 辐条 -->
        <line x1="150" y1="200" x2="100" y2="200" stroke="#333" stroke-width="2" />
        <line x1="150" y1="200" x2="150" y2="150" stroke="#333" stroke-width="2" />
        <line x1="150" y1="200" x2="200" y2="200" stroke="#333" stroke-width="2" />
        <line x1="150" y1="200" x2="150" y2="250" stroke="#333" stroke-width="2" />
        <line x1="150" y1="200" x2="115" y2="165" stroke="#333" stroke-width="2" />
        <line x1="150" y1="200" x2="185" y2="165" stroke="#333" stroke-width="2" />
        <line x1="150" y1="200" x2="115" y2="235" stroke="#333" stroke-width="2" />
        <line x1="150" y1="200" x2="185" y2="235" stroke="#333" stroke-width="2" />

        <line x1="400" y1="200" x2="350" y2="200" stroke="#333" stroke-width="2" />
        <line x1="400" y1="200" x2="400" y2="150" stroke="#333" stroke-width="2" />
        <line x1="400" y1="200" x2="450" y2="200" stroke="#333" stroke-width="2" />
        <line x1="400" y1="200" x2="400" y2="250" stroke="#333" stroke-width="2" />
        <line x1="400" y1="200" x2="365" y2="165" stroke="#333" stroke-width="2" />
        <line x1="400" y1="200" x2="435" y2="165" stroke="#333" stroke-width="2" />
        <line x1="400" y1="200" x2="365" y2="235" stroke="#333" stroke-width="2" />
        <line x1="400" y1="200" x2="435" y2="235" stroke="#333" stroke-width="2" />

        <!-- 车把 -->
        <line x1="150" y1="100" x2="100" y2="80" stroke="#333" stroke-width="6" fill="none" />
        <line x1="150" y1="100" x2="200" y2="80" stroke="#333" stroke-width="6" fill="none" />
        <line x1="100" y1="80" x2="80" y2="85" stroke="#333" stroke-width="6" fill="none" />
        <line x1="200" y1="80" x2="220" y2="85" stroke="#333" stroke-width="6" fill="none" />

        <!-- 座椅 -->
        <rect x="280" y="80" width="40" height="15" rx="5" fill="#333" />
        <line x1="250" y1="100" x2="300" y2="90" stroke="#333" stroke-width="5" fill="none" />

        <!-- 链条和齿轮 -->
        <circle cx="150" cy="200" r="15" fill="#333" />
        <circle cx="350" cy="150" r="10" fill="#333" />
        <path d="M165,200 C180,190 250,160 340,150" stroke="#333" stroke-width="3" fill="none" />
    </svg>
    `;

    svgContainer.innerHTML = bikeSVG;
}

// 为自行车类型卡片添加交互
function setupBikeTypeCards() {
    const bikeTypeCards = document.querySelectorAll('.bike-type-card');

    bikeTypeCards.forEach(card => {
        card.addEventListener('click', function() {
            const type = this.getAttribute('data-type');
            showBikeTypeInfo(type);
        });
    });
}

// 显示自行车类型信息
function showBikeTypeInfo(type) {
    // 这里可以添加显示详细信息的逻辑
    // 简单示例：更改SVG颜色来表示不同类型
    const svgContainer = document.querySelector('.bike-svg-container');
    let color = '#333';

    switch(type) {
        case 'road':
            color = '#2980b9'; // 蓝色
            break;
        case 'mountain':
            color = '#27ae60'; // 绿色
            break;
        case 'city':
            color = '#e74c3c'; // 红色
            break;
        case 'electric':
            color = '#f39c12'; // 橙色
            break;
    }

    // 更新SVG颜色
    const svgElements = svgContainer.querySelectorAll('path, circle, line, rect');
    svgElements.forEach(element => {
        if (element.tagName !== 'circle' || element.getAttribute('r') > 40) {
            element.setAttribute('stroke', color);
        }
        if (element.tagName === 'rect') {
            element.setAttribute('fill', color);
        }
        if (element.tagName === 'circle' && element.getAttribute('r') <= 15) {
            element.setAttribute('fill', color);
        }
    });
}

// 为自行车部件添加交互
function setupPartItems() {
    const partItems = document.querySelectorAll('.part-item');

    partItems.forEach(item => {
        item.addEventListener('click', function() {
            const part = this.getAttribute('data-part');
            highligtBikePart(part);
        });
    });
}

// 高亮显示自行车部件
function highlightBikePart(part) {
    // 重置所有部件颜色
    const svgContainer = document.querySelector('.bike-svg-container');
    const svgElements = svgContainer.querySelectorAll('path, circle, line, rect');

    svgElements.forEach(element => {
        if (element.tagName !== 'circle' || element.getAttribute('r') > 40) {
            element.setAttribute('stroke', '#333');
        }
        if (element.tagName === 'rect') {
            element.setAttribute('fill', '#333');
        }
        if (element.tagName === 'circle' && element.getAttribute('r') <= 15) {
            element.setAttribute('fill', '#333');
        }
    });

    // 高亮选中的部件
    switch(part) {
        case 'frame':
            // 高亮车架
            svgContainer.queySelectorAll('path').forEach(path => {
                path.setAttribute('stroke', '#e74c3c');
            });
            break;
        case 'wheels':
            // 高亮车轮
            svgContainer.querySelectorAll('circle').forEach(circle => {
                if (circle.getAttribute('r') >= 40) {
                    circle.setAttribute('stroke', '#e74c3c');
                }
            });
            break;
        case 'brakes':
            // 高亮刹车 (简化为高亮车把)
            svgContainer.querySelectorAll('line').forEach(line => {
                const x1 = parseInt(line.getAttribute('x1'));
                const y1 = parseInt(line.getAttribute('y1'));
                const x2 = parseInt(line.getAttribute('x2'));
                const y2 = parseInt(line.getAttribute('y2'));

                if ((x1 === 100 && y1 === 80 && x2 === 80 && y2 === 85) ||
                    (x1 === 200 && y1 === 80 && x2 === 220 && y2 === 85)) {
                    line.setAttribute('stroke', '#e74c3c');
                }
            });
            break;
        case 'gears':
            // 高亮齿轮
            svgContainer.querySelectorAll('circle').forEach(circle => {
                if (circle.getAttribute('r') <= 15) {
                    circle.setAttribute('fill', '#e74c3c');
                }
            });
            svgContainer.querySelectorAll('path').forEach(path => {
                const d = path.gtAttribute('d');
                if (d && d.includes('C180,190 250,160 340,150')) {
                    path.setAttribute('stroke', '#e74c3c');
                }
            });
            break;
    }
}

// 平滑滚动
function setupSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function(e) {
            e.preventDefault();

            const targetId = this.getAttribute('href');
            const targetElement = document.querySelector(targetId);

            if (targetElement) {
                window.scrollTo({
                    top: targetElement.offsetTop - 80, // 考虑导航栏高度
                    behavior: 'smooth'
                });
            }
        });
    });
}