import { useState } from 'react'
import type { CreatePortfolioGroupRequest } from '../../types/portfolioGroup'
import './CreateGroupModal.css'

interface CreateGroupModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (request: CreatePortfolioGroupRequest) => void
  isLoading: boolean
}

export function CreateGroupModal({ isOpen, onClose, onSubmit, isLoading }: CreateGroupModalProps) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  if (!isOpen) return null

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    onSubmit({
      name: name.trim(),
      description: description.trim() || undefined
    })
  }

  const handleClose = () => {
    setName('')
    setDescription('')
    onClose()
  }

  return (
    <div className="modal-overlay" onClick={handleClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Create Portfolio Group</h3>
          <button className="modal-close-btn" onClick={handleClose}>&times;</button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            <div className="form-group">
              <label htmlFor="group-name">Name</label>
              <input
                id="group-name"
                type="text"
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder="e.g. Retirement Portfolio"
                maxLength={100}
                required
                autoFocus
              />
            </div>
            <div className="form-group">
              <label htmlFor="group-description">Description (optional)</label>
              <textarea
                id="group-description"
                value={description}
                onChange={e => setDescription(e.target.value)}
                placeholder="Brief description of this portfolio group..."
                rows={3}
              />
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={handleClose}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={!name.trim() || isLoading}>
              {isLoading ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
