import type { ReactNode } from 'react'
import './Modal.css'

interface ModalProps {
  title: string
  children: ReactNode
  actions: ReactNode
  onDismiss?: () => void
}

export default function Modal({ title, children, actions, onDismiss }: ModalProps) {
  return (
    <div className="modal-overlay" onClick={onDismiss}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h2 className="modal__title">{title}</h2>
        <div className="modal__body">{children}</div>
        <div className="modal__actions">{actions}</div>
      </div>
    </div>
  )
}
