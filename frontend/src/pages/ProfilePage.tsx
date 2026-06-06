import { useState, FormEvent } from 'react';
import { useUser } from '../stores/authStore';
import { updateProfile, AuthError } from '../services/authService';
import './ProfilePage.css';

export function ProfilePage() {
  const user = useUser();

  // Profile form state
  const [name, setName] = useState(user?.name || '');
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);

  const handleProfileSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setProfileError(null);
    setProfileSuccess(null);
    setProfileLoading(true);

    try {
      await updateProfile({ name: name.trim() || undefined });
      setProfileSuccess('Profile updated successfully!');
    } catch (error) {
      if (error instanceof AuthError) {
        setProfileError(error.message);
      } else {
        setProfileError('Failed to update profile. Please try again.');
      }
    } finally {
      setProfileLoading(false);
    }
  };

  const formatDate = (dateString: string | null | undefined) => {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  if (!user) {
    return null;
  }

  return (
    <div className="profile-page">
      <h1 className="profile-title">Profile Settings</h1>

      {/* User Info Section */}
      <section className="profile-section">
        <h2 className="section-title">Account Information</h2>
        <div className="info-grid">
          <div className="info-item">
            <label>Email</label>
            <span>{user.email}</span>
          </div>
          <div className="info-item">
            <label>Email Verified</label>
            <span className={user.emailVerified ? 'verified' : 'not-verified'}>
              {user.emailVerified ? 'Yes' : 'No'}
            </span>
          </div>
          <div className="info-item">
            <label>Member Since</label>
            <span>{formatDate(user.createdAt)}</span>
          </div>
          <div className="info-item">
            <label>Last Login</label>
            <span>{formatDate(user.lastLoginAt)}</span>
          </div>
        </div>
      </section>

      {/* Update Profile Section */}
      <section className="profile-section">
        <h2 className="section-title">Update Profile</h2>
        {profileSuccess && <div className="success-message">{profileSuccess}</div>}
        {profileError && <div className="error-message">{profileError}</div>}
        <form onSubmit={handleProfileSubmit} className="profile-form">
          <div className="form-group">
            <label htmlFor="name">Display Name</label>
            <input
              id="name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Enter your display name"
              maxLength={255}
            />
          </div>
          <button
            type="submit"
            className="submit-button"
            disabled={profileLoading}
          >
            {profileLoading ? 'Saving...' : 'Save Changes'}
          </button>
        </form>
      </section>

    </div>
  );
}
