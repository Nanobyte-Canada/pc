# Cloud Run Module
# Deploys the backend Spring Boot application to Cloud Run

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

variable "service_name" {
  description = "Cloud Run service name"
  type        = string
}

variable "image" {
  description = "Container image to deploy"
  type        = string
}

variable "environment" {
  description = "Environment (dev/prod)"
  type        = string
}

variable "cloud_sql_connection_name" {
  description = "Cloud SQL connection name"
  type        = string
}

variable "vpc_connector_id" {
  description = "VPC connector ID for private Cloud SQL access"
  type        = string
}

variable "min_instances" {
  description = "Minimum number of instances"
  type        = number
  default     = 0
}

variable "max_instances" {
  description = "Maximum number of instances"
  type        = number
  default     = 10
}

variable "db_password_secret_id" {
  description = "Secret Manager secret ID for database password"
  type        = string
}

resource "google_cloud_run_v2_service" "backend" {
  name     = var.service_name
  location = var.region
  project  = var.project_id

  template {
    containers {
      image = var.image

      ports {
        container_port = 8080
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = var.environment
      }

      env {
        name  = "APP_ENVIRONMENT"
        value = var.environment
      }

      env {
        name  = "DATABASE_URL"
        value = "jdbc:postgresql://localhost:5432/portfolio"
      }

      env {
        name  = "DATABASE_USERNAME"
        value = "portfolio"
      }

      env {
        name = "DATABASE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = var.db_password_secret_id
            version = "latest"
          }
        }
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
      }

      startup_probe {
        http_get {
          path = "/health"
          port = 8080
        }
        initial_delay_seconds = 10
        timeout_seconds       = 3
        period_seconds        = 10
        failure_threshold     = 3
      }

      liveness_probe {
        http_get {
          path = "/health"
          port = 8080
        }
        timeout_seconds   = 3
        period_seconds    = 30
        failure_threshold = 3
      }
    }

    # Cloud SQL Auth Proxy sidecar
    containers {
      name  = "cloud-sql-proxy"
      image = "gcr.io/cloud-sql-connectors/cloud-sql-proxy:2.8.0"

      args = [
        "--private-ip",
        var.cloud_sql_connection_name
      ]
    }

    vpc_access {
      connector = var.vpc_connector_id
      egress    = "PRIVATE_RANGES_ONLY"
    }

    scaling {
      min_instance_count = var.min_instances
      max_instance_count = var.max_instances
    }

    service_account = google_service_account.cloud_run_sa.email
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }
}

resource "google_service_account" "cloud_run_sa" {
  project      = var.project_id
  account_id   = "${var.service_name}-sa"
  display_name = "Cloud Run Service Account for ${var.service_name}"
}

# IAM binding for Cloud SQL access
resource "google_project_iam_member" "cloud_sql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

# IAM binding for Secret Manager access
resource "google_secret_manager_secret_iam_member" "db_password_access" {
  project   = var.project_id
  secret_id = var.db_password_secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

output "service_url" {
  description = "Cloud Run service URL"
  value       = google_cloud_run_v2_service.backend.uri
}

output "service_account_email" {
  description = "Cloud Run service account email"
  value       = google_service_account.cloud_run_sa.email
}
