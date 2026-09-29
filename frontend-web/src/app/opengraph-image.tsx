import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { ImageResponse } from 'next/og'

export const alt = 'Project Guard — 계약하기 전, 등기부등본부터 확인하세요'
export const size = { width: 1200, height: 630 }
export const contentType = 'image/png'

export default async function Image() {
  const notoSansKrBold = await readFile(join(process.cwd(), 'src/assets/NotoSansKR-Bold.ttf'))

  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'linear-gradient(to bottom, #fff7ed, #ffffff)',
          fontFamily: 'Noto Sans KR',
        }}
      >
        <div
          style={{
            fontSize: 28,
            fontWeight: 700,
            color: '#ea580c',
            background: '#ffedd5',
            padding: '10px 28px',
            borderRadius: 999,
            marginBottom: 32,
            display: 'flex',
          }}
        >
          전/월세 계약 전 필수 체크
        </div>
        <div
          style={{
            fontSize: 72,
            fontWeight: 700,
            color: '#09090b',
            textAlign: 'center',
            lineHeight: 1.3,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <span>계약하기 전,</span>
          <span>등기부등본부터 확인하세요</span>
        </div>
        <div style={{ fontSize: 32, color: '#52525b', marginTop: 32, display: 'flex' }}>Project Guard</div>
      </div>
    ),
    {
      ...size,
      fonts: [{ name: 'Noto Sans KR', data: notoSansKrBold, weight: 700, style: 'normal' }],
    },
  )
}
