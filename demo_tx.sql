schemas: demo_tx

/* 创建Table */

CREATE TABLE `order` (
  `order` varchar(32) NOT NULL,
  `account` varchar(32) NOT NULL,
  `product` varchar(32) NOT NULL,
  `amount` int NOT NULL,
  `value` decimal(10,2) NOT NULL,
  PRIMARY KEY (`order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3

CREATE TABLE `product` (
  `product` varchar(32) NOT NULL,
  `amount` int NOT NULL,
  `price` decimal(10,2) NOT NULL,
  PRIMARY KEY (`product`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3

CREATE TABLE `pro_log` (
  `order` varchar(32) NOT NULL,
  `product` varchar(32) NOT NULL,
  `amount` int NOT NULL,
  PRIMARY KEY (`order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3

CREATE TABLE `account` (
  `account` varchar(32) NOT NULL,
  `value` decimal(10,2) NOT NULL,
  PRIMARY KEY (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3

CREATE TABLE `acc_log` (
  `order` varchar(32) NOT NULL,
  `account` varchar(32) NOT NULL,
  `value` decimal(10,2) NOT NULL,
  PRIMARY KEY (`order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3

/* 创建Table的数据 */

INSERT INTO `demo_tx`.`product`
(`product`,
`amount`,
`price`)
VALUES
('apple',
10000,
1.00);

INSERT INTO `demo_tx`.`account`
(`account`,
`value`)
VALUES
('bob',
10000.00);
