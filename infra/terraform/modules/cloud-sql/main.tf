# Cloud SQL Module
# Deploys PostgreSQL database on Cloud SQL

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "us-central1"
}

variable "instance_name" {
  description = "Cloud SQL instance name"
  type        = string
}

variable "database_name" {
  description = "Database name"
  type        = string
  default     = "portfolio"
}

variable "database_user" {
  description = "Database username"
  type        = string
  default     = "portfolio"
}

variable "tier" {
  description = "Cloud SQL tier"
  type        = string
  default     = "db-f1-micro"
}

variable "vpc_network_id" {
  description = "VPC network ID for private IP"
  type        = string
}

variable "environment" {
  description = "Environment (dev/prod)"
  type        = string
}

# Cloud SQL instance
resource "google_sql_database_instance" "postgres" {
  project             = var.project_id
  name                = var.instance_name
  database_version    = "POSTGRES_16"
  region              = var.region
  deletion_protection = var.environment == "prod" ? true : false

  settings {
    tier = var.tier

    ip_configuration {
      ipv4_enabled    = false
      private_network = var.vpc_network_id
    }

    backup_configuration {
      enabled                        = true
      start_time                     = "03:00"
      point_in_time_recovery_enabled = var.environment == "prod" ? true : false
      backup_retention_settings {
        retained_backups = var.environment == "prod" ? 30 : 7
      }
    }

    maintenance_window {
      day  = 7  # Sunday
      hour = 4  # 4 AM UTC
    }

    insights_config {
      query_insights_enabled  = true
      query_string_length     = 1024
      record_application_tags = true
      record_client_address   = false
    }
  }
}

# Database
resource "google_sql_database" "database" {
  project  = var.project_id
  name     = var.database_name
  instance = google_sql_database_instance.postgres.name
}

# Database user
resource "google_sql_user" "user" {
  project  = var.project_id
  name     = var.database_user
  instance = google_sql_database_instance.postgres.name
  password = random_password.db_password.result
}

# Generate random password
resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# Store password in Secret Manager
resource "google_secret_manager_secret" "db_password" {
  project   = var.project_id
  secret_id = "${var.instance_name}-db-password"

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = random_password.db_password.result
}

output "connection_name" {
  description = "Cloud SQL connection name"
  value       = google_sql_database_instance.postgres.connection_name
}

output "private_ip_address" {
  description = "Private IP address"
  value       = google_sql_database_instance.postgres.private_ip_address
}

output "database_name" {
  description = "Database name"
  value       = google_sql_database.database.name
}

output "database_user" {
  description = "Database username"
  value       = google_sql_user.user.name
}

output "password_secret_id" {
  description = "Secret Manager secret ID for database password"
  value       = google_secret_manager_secret.db_password.secret_id
}
