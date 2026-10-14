CREATE TABLE `place` (
  `placeId` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) DEFAULT NULL,
  `address` VARCHAR(255) DEFAULT NULL,
  `latitude` DOUBLE DEFAULT NULL,
  `longitude` DOUBLE DEFAULT NULL,
  PRIMARY KEY (`placeId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `place_types` (
  `place_id` VARCHAR(255) NOT NULL,
  `types` ENUM('ATTRACTION','BATHROOM','HOSPITAL','HOTEL','OTHER','PARK','RESTAURANT') NOT NULL,
  PRIMARY KEY (`place_id`, `types`),
  CONSTRAINT `fk_place_types_place` FOREIGN KEY (`place_id`) REFERENCES `place` (`placeId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;