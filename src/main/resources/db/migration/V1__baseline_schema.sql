CREATE TABLE `user` (
  `userId` BINARY(16) NOT NULL,
  `admin` BIT(1) DEFAULT NULL,
  `displayName` VARCHAR(255) DEFAULT NULL,
  `email` VARCHAR(2000) DEFAULT NULL,
  PRIMARY KEY (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `review` (
  `reviewId` BINARY(16) NOT NULL,
  `createdAt` DATETIME(6) DEFAULT NULL,
  `placeId` VARCHAR(255) DEFAULT NULL,
  `rating` INT DEFAULT NULL,
  `text` VARCHAR(2000) DEFAULT NULL,
  `userId` BINARY(16) DEFAULT NULL,
  PRIMARY KEY (`reviewId`),
  UNIQUE KEY `uk_review_user_place` (`userId`, `placeId`),
  KEY `idx_review_place_id` (`placeId`),
  CONSTRAINT `ck_review_rating_range` CHECK (`rating` >= 1 AND `rating` <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `business_claim` (
  `claimId` BINARY(16) NOT NULL,
  `createdAt` DATETIME(6) NOT NULL,
  `placeId` VARCHAR(255) NOT NULL,
  `reviewedAt` DATETIME(6) DEFAULT NULL,
  `reviewedBy` BINARY(16) DEFAULT NULL,
  `status` ENUM('APPROVED','PENDING','REJECTED') NOT NULL,
  `userId` BINARY(16) NOT NULL,
  PRIMARY KEY (`claimId`),
  KEY `idx_business_claim_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
