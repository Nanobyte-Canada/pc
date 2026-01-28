# Development Environment Configuration

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  # Uncomment and configure for remote state
  # backend "gcs" {
  #   bucket = "portfolio-terraform-state-dev"
  #   prefix = "terraform/state"
  # }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# VPC Network
resource "google_compute_network" "vpc" {
  project                 = var.project_id
  name                    = "portfolio-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "subnet" {
  project       = var.project_id
  name          = "portfolio-subnet"
  ip_cidr_range = "10.0.0.0/24"
  region        = var.region
  network       = google_compute_network.vpc.id

  private_ip_google_access = true
}

# VPC Connector for Cloud Run
resource "google_vpc_access_connector" "connector" {
  project       = var.project_id
  name          = "portfolio-connector"
  region        = var.region
  ip_cidr_range = "10.8.0.0/28"
  network       = google_compute_network.vpc.name
}

# Cloud SQL
module "cloud_sql" {
  source = "../../modules/cloud-sql"

  project_id     = var.project_id
  region         = var.region
  instance_name  = "portfolio-db-dev"
  database_name  = "portfolio"
  database_user  = "portfolio"
  tier           = "db-f1-micro"
  vpc_network_id = google_compute_network.vpc.id
  environment    = "dev"
}

# Cloud Storage for Frontend
module "cloud_storage" {
  source = "../../modules/cloud-storage"

  project_id  = var.project_id
  bucket_name = "portfolio-frontend-dev-${var.project_id}"
  location    = "US"
  environment = "dev"
}

# Workload Identity for GitHub Actions
module "workload_identity" {
  source = "../../modules/workload-identity"

  project_id     = var.project_id
  project_number = var.project_number
  github_org     = var.github_org
  github_repo    = var.github_repo
}

# Note: Cloud Run module requires the container image to exist first
# Uncomment after initial deployment
# module "cloud_run" {
#   source = "../../modules/cloud-run"
#
#   project_id                = var.project_id
#   region                    = var.region
#   service_name              = "portfolio-backend"
#   image                     = "${var.region}-docker.pkg.dev/${var.project_id}/portfolio/portfolio-backend:latest"
#   environment               = "dev"
#   cloud_sql_connection_name = module.cloud_sql.connection_name
#   vpc_connector_id          = google_vpc_access_connector.connector.id
#   min_instances             = 0
#   max_instances             = 5
#   db_password_secret_id     = module.cloud_sql.password_secret_id
# }
