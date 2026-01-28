# Production Environment Configuration

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
  #   bucket = "portfolio-terraform-state-prod"
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

# Cloud SQL - Production settings
module "cloud_sql" {
  source = "../../modules/cloud-sql"

  project_id     = var.project_id
  region         = var.region
  instance_name  = "portfolio-db-prod"
  database_name  = "portfolio"
  database_user  = "portfolio"
  tier           = "db-custom-2-4096"  # 2 vCPU, 4GB RAM
  vpc_network_id = google_compute_network.vpc.id
  environment    = "prod"
}

# Cloud Storage for Frontend
module "cloud_storage" {
  source = "../../modules/cloud-storage"

  project_id  = var.project_id
  bucket_name = "portfolio-frontend-prod-${var.project_id}"
  location    = "US"
  environment = "prod"
}

# Workload Identity for GitHub Actions
module "workload_identity" {
  source = "../../modules/workload-identity"

  project_id     = var.project_id
  project_number = var.project_number
  github_org     = var.github_org
  github_repo    = var.github_repo
}

# Note: Cloud Run and Load Balancer modules require additional setup
# Uncomment after initial deployment and domain verification
