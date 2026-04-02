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
                                 UNIQUE KEY `nodeId` (`nodeId`),
                                 KEY `postcode` (`postcode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 2026-04-02 15:24:48 UTC
