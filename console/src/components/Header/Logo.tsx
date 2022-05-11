import React from 'react'
import { Link } from 'react-router-dom'
import { useCurrentUser } from '@/hooks/useCurrentUser'
import smallLogoImg from '@/assets/logo_small.svg'
import normalLogoImg from '@/assets/logo_normal_en_white.svg'
import Text from '../Text'

export interface ILogoProps {
    expanded?: boolean
}

export default function Logo({ expanded = true }: ILogoProps) {
    // eslint-disable-next-line react-hooks/exhaustive-deps
    const { currentUser } = useCurrentUser()

    const LogoText = expanded ? (
        <Text
            size='large'
            style={{
                display: 'flex',
                fontSize: '30px',
                color: '#fff',
            }}
        >
            <img width='150px' src={normalLogoImg} alt='logo' />
        </Text>
    ) : (
        <Text
            size='large'
            style={{
                display: 'flex',
                fontSize: '20px',
                color: '#fff',
            }}
        >
            <img src={smallLogoImg} alt='logo' />
        </Text>
    )

    if (!currentUser)
        return (
            <div
                style={{
                    flex: `0 0 ${expanded ? 200 : 68}px`,
                    display: 'flex',
                    flexDirection: 'row',
                    textDecoration: 'none',
                    alignItems: 'center',
                    justifyContent: 'center',
                    transition: 'width 200ms cubic-bezier(0.7, 0.1, 0.33, 1) 0ms',
                }}
            >
                {LogoText}
            </div>
        )

    return (
        <Link
            style={{
                flex: `0 0 ${expanded ? 200 : 68}px`,
                display: 'flex',
                flexDirection: 'row',
                textDecoration: 'none',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'width 200ms cubic-bezier(0.7, 0.1, 0.33, 1) 0ms',
            }}
            to='/'
        >
            {LogoText}
        </Link>
    )
}
