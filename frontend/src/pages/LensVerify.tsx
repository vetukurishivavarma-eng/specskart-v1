import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { lens } from '../lib/lens'

export default function LensVerify() {
  const { token } = useParams()
  const [state, setState] = useState<'checking' | 'ok' | 'fail'>('checking')

  useEffect(() => {
    if (!token) { setState('fail'); return }
    lens.verify(token).then((r) => setState(r.verified ? 'ok' : 'fail')).catch(() => setState('fail'))
  }, [token])

  return (
    <div className="container-x max-w-md py-20 text-center">
      {state === 'checking' && <p className="text-ink/50">Verifying…</p>}
      {state === 'ok' && (
        <>
          <h1 className="text-2xl text-moss">✅ Number verified</h1>
          <p className="mt-3 text-ink/60">
            You're all set — go back to the tab where you started configuring your lenses to continue,
            or carry on here.
          </p>
          <Link to="/lens" className="btn-primary mt-6 inline-block">Continue configuring my lenses</Link>
        </>
      )}
      {state === 'fail' && (
        <>
          <h1 className="text-2xl text-clay">Link expired or invalid</h1>
          <p className="mt-3 text-ink/60">Start again and we'll send you a fresh link.</p>
          <Link to="/lens" className="btn-primary mt-6 inline-block">Start over</Link>
        </>
      )}
    </div>
  )
}
