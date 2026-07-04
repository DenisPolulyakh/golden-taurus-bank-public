import React, { useState } from 'react';

const PieChartComponent = ({ data, formatAmount }) => {
    const [activeIndex, setActiveIndex] = useState(null);

    const getTotalDistributionAmount = () => {
        return data.reduce((sum, item) => sum + item.amount, 0);
    };

    const getPercentage = (amount) => {
        const total = getTotalDistributionAmount();
        if (total === 0) return 0;
        return (amount / total) * 100;
    };

    const getArcLength = (percentage) => {
        return (percentage / 100) * 360;
    };

    const hasData = data.length > 0 && data.some(item => item.amount > 0);

    if (!hasData) {
        return (
            <div className="pie-chart-empty">
                <p style={{ color: '#a0aec0' }}>Нет данных для отображения</p>
            </div>
        );
    }

    const PieChart = () => {
        const radius = 120;
        const centerX = 150;
        const centerY = 150;
        const total = getTotalDistributionAmount();
        let currentAngle = -90;

        if (total === 0 || data.length === 0) {
            return null;
        }

        return (
            <svg width="300" height="300" viewBox="0 0 300 300">
                {data.map((item, index) => {
                    const percentage = getPercentage(item.amount);
                    const arcLength = getArcLength(percentage);
                    const endAngle = currentAngle + arcLength;

                    const startRad = (currentAngle * Math.PI) / 180;
                    const endRad = (endAngle * Math.PI) / 180;

                    const offset = activeIndex === index ? 20 : 0;
                    const offsetAngle = (startRad + endRad) / 2;
                    const offsetX = offset * Math.cos(offsetAngle);
                    const offsetY = offset * Math.sin(offsetAngle);

                    const cX = centerX + offsetX;
                    const cY = centerY + offsetY;

                    const x1 = cX + radius * Math.cos(startRad);
                    const y1 = cY + radius * Math.sin(startRad);
                    const x2 = cX + radius * Math.cos(endRad);
                    const y2 = cY + radius * Math.sin(endRad);

                    const largeArc = arcLength > 180 ? 1 : 0;

                    const pathData = `
                        M ${cX} ${cY}
                        L ${x1} ${y1}
                        A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2}
                        Z
                    `;

                    const midAngle = (currentAngle + arcLength / 2) * Math.PI / 180;
                    const textRadius = radius * 0.65;
                    const textX = cX + textRadius * Math.cos(midAngle);
                    const textY = cY + textRadius * Math.sin(midAngle);

                    const itemColor = item.color || '#CCCCCC';
                    const strokeWidth = activeIndex === index ? 4 : 2;
                    const strokeColor = activeIndex === index ? '#000' : 'white';
                    const opacity = activeIndex === null ? 1 : (activeIndex === index ? 1 : 0.4);

                    const result = (
                        <g
                            key={index}
                            onClick={() => {
                                if (activeIndex === index) {
                                    setActiveIndex(null);
                                } else {
                                    setActiveIndex(index);
                                }
                            }}
                            style={{ cursor: 'pointer' }}
                        >
                            <path
                                d={pathData}
                                fill={itemColor}
                                stroke={strokeColor}
                                strokeWidth={strokeWidth}
                                opacity={opacity}
                                style={{ transition: 'all 0.3s ease' }}
                            />
                            {percentage > 5 && (
                                <text
                                    x={textX}
                                    y={textY}
                                    textAnchor="middle"
                                    fill="white"
                                    fontSize="12"
                                    fontWeight="bold"
                                    opacity={opacity}
                                    style={{ textShadow: '0 1px 3px rgba(0,0,0,0.5)' }}
                                >
                                    {Math.round(percentage)}%
                                </text>
                            )}
                        </g>
                    );

                    currentAngle = endAngle;
                    return result;
                })}
                <circle cx="150" cy="150" r="50" fill="white" opacity="0.9" />
                <text x="150" y="145" textAnchor="middle" fontSize="14" fill="#333" fontWeight="bold">
                    {formatAmount(getTotalDistributionAmount())}
                </text>
                <text x="150" y="165" textAnchor="middle" fontSize="11" fill="#666">
                    Всего ₽
                </text>
            </svg>
        );
    };

    return (
        <div className="pie-chart-wrapper" style={{ flexDirection: 'column', gap: '30px' }}>
            <PieChart />

            {/* Легенда в 3 колонки */}
            <div className="pie-legend-grid">
                {data.map((item, index) => {
                    const isActive = activeIndex === index;
                    return (
                        <div
                            key={index}
                            className={`legend-item ${isActive ? 'active' : ''}`}
                            onClick={() => {
                                if (activeIndex === index) {
                                    setActiveIndex(null);
                                } else {
                                    setActiveIndex(index);
                                }
                            }}
                            style={{
                                cursor: 'pointer',
                                background: isActive ? '#e0e7ff' : '#f8f9fa',
                                border: isActive ? '2px solid #667eea' : '2px solid transparent',
                                transition: 'all 0.3s ease',
                                borderRadius: '8px',
                                padding: '6px 12px',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px'
                            }}
                        >
                            <span
                                className="legend-color"
                                style={{
                                    backgroundColor: item.color || '#CCCCCC',
                                    opacity: isActive ? 1 : 0.7,
                                    width: '14px',
                                    height: '14px',
                                    borderRadius: '4px',
                                    flexShrink: 0,
                                    border: '1px solid rgba(0,0,0,0.1)'
                                }}
                            />
                            <span className="legend-name" style={{
                                fontSize: '13px',
                                fontWeight: isActive ? 700 : 500,
                                color: isActive ? '#667eea' : '#333',
                                flex: 1,
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap'
                            }}>
                                {item.name}
                            </span>
                            <span className="legend-amount" style={{
                                fontSize: '12px',
                                fontWeight: 600,
                                color: '#495057',
                                whiteSpace: 'nowrap'
                            }}>
                                {formatAmount(item.amount)} ₽
                            </span>
                            <span className="legend-percentage" style={{
                                fontSize: '11px',
                                color: '#868e96',
                                fontWeight: 500,
                                minWidth: '40px',
                                textAlign: 'right'
                            }}>
                                ({Math.round(getPercentage(item.amount))}%)
                            </span>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default PieChartComponent;