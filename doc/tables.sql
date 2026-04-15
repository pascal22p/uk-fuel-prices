-- Adminer 5.3.0 MariaDB 12.0.2-MariaDB-ubu2404-log dump

SET NAMES utf8;
SET time_zone = '+00:00';
SET foreign_key_checks = 0;

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `fuel_stations`;
CREATE TABLE `fuel_stations` (
                                 `nodeId` varchar(128) NOT NULL,
                                 `tradingName` varchar(128) NOT NULL,
                                 `isSameTradingAndBrandName` tinyint(1) DEFAULT NULL,
                                 `brandName` varchar(128) NOT NULL,
                                 `temporaryClosure` tinyint(1) DEFAULT NULL,
                                 `permanentClosure` tinyint(1) DEFAULT NULL,
                                 `isMotorwayServiceStation` tinyint(1) DEFAULT NULL,
                                 `isSupermarketServiceStation` tinyint(1) DEFAULT NULL,
                                 `fuelTypes` varchar(256) NOT NULL,
                                 `addressLine1` varchar(256) DEFAULT NULL,
                                 `addressLine2` varchar(256) DEFAULT NULL,
                                 `city` varchar(256) NOT NULL,
                                 `country` varchar(256) DEFAULT NULL,
                                 `county` varchar(256) DEFAULT NULL,
                                 `postcode` varchar(128) NOT NULL,
                                 `latitude` double NOT NULL,
                                 `longitude` double NOT NULL,
                                 `lastUpdated` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE current_timestamp(),
                                 PRIMARY KEY (`nodeId`),
                                 KEY `postcode` (`postcode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `fuel_prices`;
CREATE TABLE `fuel_prices` (
                               `nodeId` varchar(128) NOT NULL,
                               `price` double NOT NULL,
                               `fuelType` varchar(128) NOT NULL,
                               `priceLastUpdated` timestamp NOT NULL,
                               `priceChangeEffectiveTimestamp` timestamp NOT NULL,
                               PRIMARY KEY (`nodeId`),
                               CONSTRAINT `fuel_prices_ibfk_1` FOREIGN KEY (`nodeId`) REFERENCES `fuel_stations` (`nodeId`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `fuel_sessions`;
CREATE TABLE `fuel_sessions` (
                                  `sessionId` varchar(36) NOT NULL,
                                  `sessionData` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`sessionData`)),
                                  `timeStamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`sessionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP EVENT IF EXISTS `delete_sessions`;
CREATE EVENT `delete_sessions` ON SCHEDULE EVERY 1 MINUTE STARTS '2024-07-15 14:50:12' ON COMPLETION NOT PRESERVE ENABLE DO DELETE FROM fuel_sessions
    WHERE UNIX_TIMESTAMP(timestamp) < UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 1 HOUR));
-- 2026-04-02 15:24:48 UTC
