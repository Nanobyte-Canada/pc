# Cloud Storage Module
# Hosts static frontend files

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

variable "bucket_name" {
  description = "Cloud Storage bucket name"
  type        = string
}

variable "location" {
  description = "Bucket location"
  type        = string
  default     = "US"
}

variable "environment" {
  description = "Environment (dev/prod)"
  type        = string
}

# Frontend static hosting bucket
resource "google_storage_bucket" "frontend" {
  project  = var.project_id
  name     = var.bucket_name
  location = var.location

  website {
    main_page_suffix = "index.html"
    not_found_page   = "index.html"  # SPA routing fallback
  }

  cors {
    origin          = ["*"]
    method          = ["GET", "HEAD"]
    response_header = ["Content-Type"]
    max_age_seconds = 3600
  }

  uniform_bucket_level_access = true

  versioning {
    enabled = var.environment == "prod" ? true : false
  }

  lifecycle_rule {
    condition {
      age = 30
      with_state = "ARCHIVED"
    }
    action {
      type = "Delete"
    }
  }
}

# Make bucket publicly accessible
resource "google_storage_bucket_iam_member" "public_access" {
  bucket = google_storage_bucket.frontend.name
  role   = "roles/storage.objectViewer"
  member = "allUsers"
}

output "bucket_name" {
  description = "Bucket name"
  value       = google_storage_bucket.frontend.name
}

output "bucket_url" {
  description = "Bucket URL"
  value       = google_storage_bucket.frontend.url
}

output "website_url" {
  description = "Website URL"
  value       = "https://storage.googleapis.com/${google_storage_bucket.frontend.name}"
}
