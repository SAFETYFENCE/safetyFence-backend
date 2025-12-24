# Safety Fence API 명세서

> **프로젝트**: Safety Fence - 실시간 위치 추적 및 지오펜스 시스템
> **버전**: 1.2
> **작성일**: 2025-10-25
> **최종 수정**: 2025-12-24

## 목차
1. [인증 및 사용자 관리 API](#1-인증-및-사용자-관리-api)
2. [링크(연결) 관리 API](#2-링크연결-관리-api)
3. [지오펜스 API](#3-지오펜스-api)
4. [로그 조회 API](#4-로그-조회-api)
5. [캘린더 API](#5-캘린더-api)
6. [마이페이지 API](#6-마이페이지-api)
7. [알림 (Notification) API](#7-알림-notification-api)
8. [WebSocket 실시간 위치 공유 API](#8-websocket-실시간-위치-공유-api)

---

## 1. 인증 및 사용자 관리 API

### 1.1 회원가입

**Endpoint**: `POST /user/signup`

**Description**: 새로운 사용자를 등록합니다.

**Request Body**:
```json
{
  "number": "01012345678",
  "name": "홍길동",
  "password": "password123",
  "birth": "1990-01-01",
  "homeAddress": "12345",
  "centerAddress": "67890",
  "homeStreetAddress": "서울특별시 강남구 테헤란로",
  "homeStreetAddressDetail": "101동 101호",
  "centerStreetAddress": "서울특별시 송파구 올림픽로"
}
```

**Response**:
```json
{
  "status": "success",
  "message": "회원가입이 완료되었습니다."
}
```

**Validation Rules**:
- `number`: 11자리 숫자만 허용
- `name`: 한글 2~4자
- `password`: 영문자와 숫자를 포함한 6자리 이상
- `birth`: 과거 날짜만 허용 (yyyy-MM-dd 형식)
- `homeAddress`, `centerAddress`: 5자리 우편번호

**React 예시**:
```jsx
import axios from 'axios';

const SignUp = () => {
  const [formData, setFormData] = useState({
    number: '',
    name: '',
    password: '',
    birth: '',
    homeAddress: '',
    centerAddress: '',
    homeStreetAddress: '',
    homeStreetAddressDetail: '',
    centerStreetAddress: ''
  });

  const handleSignUp = async (e) => {
    e.preventDefault();

    try {
      const response = await axios.post('/user/signup', formData);
      console.log('회원가입 성공:', response.data);
      alert('회원가입이 완료되었습니다.');
    } catch (error) {
      console.error('회원가입 실패:', error.response?.data);
      alert('회원가입에 실패했습니다.');
    }
  };

  return (
    <form onSubmit={handleSignUp}>
      <input
        type="text"
        placeholder="전화번호 (11자리)"
        value={formData.number}
        onChange={(e) => setFormData({...formData, number: e.target.value})}
      />
      <input
        type="text"
        placeholder="이름"
        value={formData.name}
        onChange={(e) => setFormData({...formData, name: e.target.value})}
      />
      <input
        type="password"
        placeholder="비밀번호"
        value={formData.password}
        onChange={(e) => setFormData({...formData, password: e.target.value})}
      />
      <input
        type="date"
        value={formData.birth}
        onChange={(e) => setFormData({...formData, birth: e.target.value})}
      />
      <button type="submit">회원가입</button>
    </form>
  );
};
```

---

### 1.2 로그인

**Endpoint**: `POST /user/signIn`

**Description**: 사용자 인증 후 API 키를 발급합니다.

**Request Body**:
```json
{
  "number": "01012345678",
  "password": "password123"
}
```

**Response**:
```json
{
  "apiKey": "generated-api-key-string",
  "userNumber": "01012345678",
  "name": "홍길동"
}
```

**React 예시**:
```jsx
import axios from 'axios';

const Login = () => {
  const [credentials, setCredentials] = useState({
    number: '',
    password: ''
  });

  const handleLogin = async (e) => {
    e.preventDefault();

    try {
      const response = await axios.post('/user/signIn', credentials);
      const { apiKey, userNumber, name } = response.data;

      // API 키를 로컬 스토리지에 저장
      localStorage.setItem('apiKey', apiKey);
      localStorage.setItem('userNumber', userNumber);
      localStorage.setItem('userName', name);

      console.log('로그인 성공:', response.data);
      alert(`${name}님, 환영합니다!`);

      // 이후 모든 요청에 헤더 추가
      axios.defaults.headers.common['X-API-Key'] = apiKey;

    } catch (error) {
      console.error('로그인 실패:', error.response?.data);
      alert('로그인에 실패했습니다.');
    }
  };

  return (
    <form onSubmit={handleLogin}>
      <input
        type="text"
        placeholder="전화번호"
        value={credentials.number}
        onChange={(e) => setCredentials({...credentials, number: e.target.value})}
      />
      <input
        type="password"
        placeholder="비밀번호"
        value={credentials.password}
        onChange={(e) => setCredentials({...credentials, password: e.target.value})}
      />
      <button type="submit">로그인</button>
    </form>
  );
};
```

---

## 2. 링크(연결) 관리 API

### 2.1 링크 목록 조회

**Endpoint**: `GET /link/list`

**Description**: 현재 사용자가 등록한 모든 링크를 조회합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Response**:
```json
[
  {
    "id": 1,
    "userNumber": "01098765432",
    "relation": "친구"
  },
  {
    "id": 2,
    "userNumber": "01011112222",
    "relation": "가족"
  }
]
```

**React 예시**:
```jsx
import axios from 'axios';
import { useEffect, useState } from 'react';

const LinkList = () => {
  const [links, setLinks] = useState([]);

  useEffect(() => {
    fetchLinks();
  }, []);

  const fetchLinks = async () => {
    try {
      const apiKey = localStorage.getItem('apiKey');
      const response = await axios.get('/link/list', {
        headers: { 'X-API-Key': apiKey }
      });
      setLinks(response.data);
    } catch (error) {
      console.error('링크 조회 실패:', error);
    }
  };

  return (
    <div>
      <h2>내 링크 목록</h2>
      <ul>
        {links.map(link => (
          <li key={link.id}>
            {link.userNumber} - {link.relation}
          </li>
        ))}
      </ul>
    </div>
  );
};
```

---

### 2.2 링크 추가

**Endpoint**: `POST /link/addUser`

**Description**: 다른 사용자를 링크로 추가합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "linkCode": "LINKA123",
  "relation": "친구"
}
```

**Response**:
```json
{
  "status": "success",
  "message": "링크가 추가되었습니다."
}
```

**React 예시**:
```jsx
const AddLink = () => {
  const [linkData, setLinkData] = useState({
    linkCode: '',
    relation: ''
  });

  const handleAddLink = async (e) => {
    e.preventDefault();

    try {
      const apiKey = localStorage.getItem('apiKey');
      const response = await axios.post('/link/addUser', linkData, {
        headers: { 'X-API-Key': apiKey }
      });

      alert('링크가 추가되었습니다.');
      setLinkData({ linkCode: '', relation: '' });

    } catch (error) {
      console.error('링크 추가 실패:', error);
      alert('링크 추가에 실패했습니다.');
    }
  };

  return (
    <form onSubmit={handleAddLink}>
      <input
        type="text"
        placeholder="링크 코드"
        value={linkData.linkCode}
        onChange={(e) => setLinkData({...linkData, linkCode: e.target.value})}
      />
      <input
        type="text"
        placeholder="관계 (예: 친구, 가족)"
        value={linkData.relation}
        onChange={(e) => setLinkData({...linkData, relation: e.target.value})}
      />
      <button type="submit">링크 추가</button>
    </form>
  );
};
```

---

### 2.3 링크 삭제

**Endpoint**: `DELETE /link/deleteUser`

**Description**: 등록된 링크를 삭제합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "number": "01098765432"
}
```

**Response**:
```json
{
  "status": "success",
  "message": "링크가 삭제되었습니다."
}
```

**React 예시**:
```jsx
const DeleteLink = ({ userNumber, onDeleted }) => {
  const handleDeleteLink = async () => {
    if (!confirm(`${userNumber}를 삭제하시겠습니까?`)) return;

    try {
      const apiKey = localStorage.getItem('apiKey');
      await axios.delete('/link/deleteUser', {
        headers: { 'X-API-Key': apiKey },
        data: { number: userNumber }
      });

      alert('링크가 삭제되었습니다.');
      onDeleted();

    } catch (error) {
      console.error('링크 삭제 실패:', error);
      alert('링크 삭제에 실패했습니다.');
    }
  };

  return (
    <button onClick={handleDeleteLink}>삭제</button>
  );
};
```

---

## 3. 지오펜스 API

### 3.1 지오펜스 목록 조회

**Endpoint**: `POST /geofence/list`

**Description**: 사용자의 모든 지오펜스를 조회합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "number": "01012345678"
}
```

**Response**:
```json
[
  {
    "id": 1,
    "name": "우리집",
    "address": "서울특별시 강남구 테헤란로 123",
    "latitude": 37.123456,
    "longitude": 127.123456,
    "type": 0,
    "startTime": null,
    "endTime": null,
    "maxSequence": 5
  },
  {
    "id": 2,
    "name": "회사",
    "address": "서울특별시 송파구 올림픽로 456",
    "latitude": 37.654321,
    "longitude": 127.654321,
    "type": 1,
    "startTime": "2025-10-25T09:00:00",
    "endTime": "2025-10-25T18:00:00",
    "maxSequence": 3
  }
]
```

**지오펜스 타입**:
- `0`: 영구 지오펜스 (항상 활성)
- `1`: 일시 지오펜스 (시작/종료 시간 지정)

**React 예시**:
```jsx
import { useEffect, useState } from 'react';
import axios from 'axios';

const GeofenceList = () => {
  const [geofences, setGeofences] = useState([]);

  useEffect(() => {
    fetchGeofences();
  }, []);

  const fetchGeofences = async () => {
    try {
      const apiKey = localStorage.getItem('apiKey');
      const userNumber = localStorage.getItem('userNumber');

      const response = await axios.post('/geofence/list',
        { number: userNumber },
        { headers: { 'X-API-Key': apiKey } }
      );

      setGeofences(response.data);
    } catch (error) {
      console.error('지오펜스 조회 실패:', error);
    }
  };

  return (
    <div>
      <h2>지오펜스 목록</h2>
      <ul>
        {geofences.map(fence => (
          <li key={fence.id}>
            <h3>{fence.name}</h3>
            <p>{fence.address}</p>
            <p>타입: {fence.type === 0 ? '영구' : '일시'}</p>
            {fence.type === 1 && (
              <p>
                시간: {fence.startTime} ~ {fence.endTime}
              </p>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
};
```

---

### 3.2 지오펜스 생성

**Endpoint**: `POST /geofence/newFence`

**Description**: 새로운 지오펜스를 생성합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "name": "우리집",
  "address": "서울특별시 강남구 테헤란로 123",
  "type": 0,
  "startTime": null,
  "endTime": null
}
```

**일시 지오펜스 예시**:
```json
{
  "name": "회사",
  "address": "서울특별시 송파구 올림픽로 456",
  "type": 1,
  "startTime": "09:00",
  "endTime": "18:00"
}
```

**Response**:
```json
{
  "id": 1,
  "name": "우리집",
  "address": "서울특별시 강남구 테헤란로 123",
  "latitude": 37.123456,
  "longitude": 127.123456,
  "type": 0,
  "startTime": null,
  "endTime": null,
  "maxSequence": 0
}
```

**React 예시**:
```jsx
const CreateGeofence = () => {
  const [fenceData, setFenceData] = useState({
    name: '',
    address: '',
    type: 0,
    startTime: '',
    endTime: ''
  });

  const handleCreateFence = async (e) => {
    e.preventDefault();

    try {
      const apiKey = localStorage.getItem('apiKey');

      // type이 0(영구)이면 시간 정보 제거
      const requestData = fenceData.type === 0
        ? { ...fenceData, startTime: null, endTime: null }
        : fenceData;

      const response = await axios.post('/geofence/newFence', requestData, {
        headers: { 'X-API-Key': apiKey }
      });

      console.log('지오펜스 생성 성공:', response.data);
      alert('지오펜스가 생성되었습니다.');

    } catch (error) {
      console.error('지오펜스 생성 실패:', error);
      alert('지오펜스 생성에 실패했습니다.');
    }
  };

  return (
    <form onSubmit={handleCreateFence}>
      <input
        type="text"
        placeholder="지오펜스 이름"
        value={fenceData.name}
        onChange={(e) => setFenceData({...fenceData, name: e.target.value})}
      />
      <input
        type="text"
        placeholder="주소"
        value={fenceData.address}
        onChange={(e) => setFenceData({...fenceData, address: e.target.value})}
      />
      <select
        value={fenceData.type}
        onChange={(e) => setFenceData({...fenceData, type: parseInt(e.target.value)})}
      >
        <option value={0}>영구</option>
        <option value={1}>일시</option>
      </select>

      {fenceData.type === 1 && (
        <>
          <input
            type="time"
            value={fenceData.startTime}
            onChange={(e) => setFenceData({...fenceData, startTime: e.target.value})}
          />
          <input
            type="time"
            value={fenceData.endTime}
            onChange={(e) => setFenceData({...fenceData, endTime: e.target.value})}
          />
        </>
      )}

      <button type="submit">생성</button>
    </form>
  );
};
```

---

### 3.3 지오펜스 진입 기록

**Endpoint**: `POST /geofence/userFenceIn`

**Description**: 사용자가 지오펜스에 진입했을 때 기록합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "geofenceId": 1
}
```

**Response**:
```json
{
  "status": "success",
  "message": "진입이 기록되었습니다."
}
```

**React 예시**:
```jsx
const RecordFenceEntry = () => {
  const recordEntry = async (geofenceId) => {
    try {
      const apiKey = localStorage.getItem('apiKey');

      await axios.post('/geofence/userFenceIn',
        { geofenceId },
        { headers: { 'X-API-Key': apiKey } }
      );

      console.log(`지오펜스 ${geofenceId} 진입 기록됨`);

    } catch (error) {
      console.error('진입 기록 실패:', error);
    }
  };

  // 실제 사용 시 위치 추적과 함께 사용
  useEffect(() => {
    const checkGeofence = (userLat, userLng, fenceLat, fenceLng, fenceId) => {
      const distance = calculateDistance(userLat, userLng, fenceLat, fenceLng);

      // 100m 이내 진입 시
      if (distance <= 100) {
        recordEntry(fenceId);
      }
    };

    // 위치 추적 로직...
  }, []);

  return null; // 백그라운드 작업
};

// Haversine 거리 계산 함수
function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371000; // 지구 반지름 (미터)
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a =
    Math.sin(dLat/2) * Math.sin(dLat/2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
}
```

---

### 3.4 지오펜스 삭제

**Endpoint**: `DELETE /geofence/deleteFence`

**Description**: 지오펜스를 삭제합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "id": 1
}
```

**Response**:
```json
{
  "status": "success",
  "message": "지오펜스가 삭제되었습니다."
}
```

**React 예시**:
```jsx
const DeleteGeofence = ({ geofenceId, onDeleted }) => {
  const handleDelete = async () => {
    if (!confirm('정말 삭제하시겠습니까?')) return;

    try {
      const apiKey = localStorage.getItem('apiKey');

      await axios.delete('/geofence/deleteFence', {
        headers: { 'X-API-Key': apiKey },
        data: { id: geofenceId }
      });

      alert('지오펜스가 삭제되었습니다.');
      onDeleted();

    } catch (error) {
      console.error('지오펜스 삭제 실패:', error);
      alert('삭제에 실패했습니다.');
    }
  };

  return (
    <button onClick={handleDelete}>삭제</button>
  );
};
```

---

## 4. 로그 조회 API

### 4.1 로그 조회

**Endpoint**: `GET /logs`

**Description**: 사용자의 지오펜스 진입 로그를 조회합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Response**:
```json
[
  {
    "id": 1,
    "location": "우리집",
    "locationAddress": "서울특별시 강남구 테헤란로 123",
    "arriveTime": "2025-10-25 09:30:00"
  },
  {
    "id": 2,
    "location": "회사",
    "locationAddress": "서울특별시 송파구 올림픽로 456",
    "arriveTime": "2025-10-25 10:15:00"
  }
]
```

**React 예시**:
```jsx
import { useEffect, useState } from 'react';
import axios from 'axios';

const LogHistory = () => {
  const [logs, setLogs] = useState([]);

  useEffect(() => {
    fetchLogs();
  }, []);

  const fetchLogs = async () => {
    try {
      const apiKey = localStorage.getItem('apiKey');
      const response = await axios.get('/logs', {
        headers: { 'X-API-Key': apiKey }
      });
      setLogs(response.data);
    } catch (error) {
      console.error('로그 조회 실패:', error);
    }
  };

  return (
    <div>
      <h2>진입 기록</h2>
      <table>
        <thead>
          <tr>
            <th>장소</th>
            <th>주소</th>
            <th>진입 시간</th>
          </tr>
        </thead>
        <tbody>
          {logs.map(log => (
            <tr key={log.id}>
              <td>{log.location}</td>
              <td>{log.locationAddress}</td>
              <td>{log.arriveTime}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
```

---

## 5. 캘린더 API

### 5.1 캘린더 데이터 조회

**Endpoint**: `GET /calendar/userData`

**Description**: 특정 날짜의 로그, 지오펜스, 이벤트 데이터를 조회합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Query Parameters**:
- `date`: 조회할 날짜 (yyyy-MM-dd 형식)

**Request Example**:
```
GET /calendar/userData?date=2025-10-25
```

**Response**:
```json
{
  "date": "2025-10-25",
  "logs": [
    {
      "logId": 1,
      "location": "우리집",
      "locationAddress": "서울특별시 강남구 테헤란로 123",
      "arriveTime": "09:30:00"
    }
  ],
  "geofences": [
    {
      "geofenceId": 1,
      "name": "회사",
      "address": "서울특별시 송파구 올림픽로 456",
      "startTime": "2025-10-25T09:00:00",
      "endTime": "2025-10-25T18:00:00"
    }
  ],
  "userEvents": [
    {
      "userEventId": 1,
      "event": "팀 미팅",
      "eventStartTime": "14:00:00"
    }
  ]
}
```

**React 예시**:
```jsx
import { useState } from 'react';
import axios from 'axios';

const Calendar = () => {
  const [selectedDate, setSelectedDate] = useState('2025-10-25');
  const [dayData, setDayData] = useState(null);

  const fetchDayData = async (date) => {
    try {
      const apiKey = localStorage.getItem('apiKey');
      const response = await axios.get('/calendar/userData', {
        headers: { 'X-API-Key': apiKey },
        params: { date }
      });
      setDayData(response.data);
    } catch (error) {
      console.error('캘린더 데이터 조회 실패:', error);
    }
  };

  useEffect(() => {
    fetchDayData(selectedDate);
  }, [selectedDate]);

  return (
    <div>
      <h2>캘린더</h2>
      <input
        type="date"
        value={selectedDate}
        onChange={(e) => setSelectedDate(e.target.value)}
      />

      {dayData && (
        <div>
          <h3>{dayData.date}</h3>

          <section>
            <h4>진입 기록</h4>
            <ul>
              {dayData.logs.map(log => (
                <li key={log.logId}>
                  {log.arriveTime} - {log.location}
                </li>
              ))}
            </ul>
          </section>

          <section>
            <h4>지오펜스</h4>
            <ul>
              {dayData.geofences.map(fence => (
                <li key={fence.geofenceId}>
                  {fence.name}: {fence.startTime} ~ {fence.endTime}
                </li>
              ))}
            </ul>
          </section>

          <section>
            <h4>일정</h4>
            <ul>
              {dayData.userEvents.map(event => (
                <li key={event.userEventId}>
                  {event.eventStartTime} - {event.event}
                </li>
              ))}
            </ul>
          </section>
        </div>
      )}
    </div>
  );
};
```

---

### 5.2 이벤트 추가

**Endpoint**: `POST /calendar/addEvent`

**Description**: 새로운 이벤트를 캘린더에 추가합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "event": "팀 미팅",
  "eventDate": "2025-10-25",
  "startTime": "14:00"
}
```

**Response**:
```json
{
  "status": "success",
  "message": "이벤트가 추가되었습니다."
}
```

**React 예시**:
```jsx
const AddEvent = () => {
  const [eventData, setEventData] = useState({
    event: '',
    eventDate: '',
    startTime: ''
  });

  const handleAddEvent = async (e) => {
    e.preventDefault();

    try {
      const apiKey = localStorage.getItem('apiKey');

      await axios.post('/calendar/addEvent', eventData, {
        headers: { 'X-API-Key': apiKey }
      });

      alert('이벤트가 추가되었습니다.');
      setEventData({ event: '', eventDate: '', startTime: '' });

    } catch (error) {
      console.error('이벤트 추가 실패:', error);
      alert('이벤트 추가에 실패했습니다.');
    }
  };

  return (
    <form onSubmit={handleAddEvent}>
      <input
        type="text"
        placeholder="이벤트명"
        value={eventData.event}
        onChange={(e) => setEventData({...eventData, event: e.target.value})}
      />
      <input
        type="date"
        value={eventData.eventDate}
        onChange={(e) => setEventData({...eventData, eventDate: e.target.value})}
      />
      <input
        type="time"
        value={eventData.startTime}
        onChange={(e) => setEventData({...eventData, startTime: e.target.value})}
      />
      <button type="submit">추가</button>
    </form>
  );
};
```

---

### 5.3 이벤트 삭제

**Endpoint**: `DELETE /calendar/deleteEvent`

**Description**: 캘린더의 이벤트를 삭제합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Query Parameters**:
- `userEventId`: 삭제할 이벤트 ID

**Request Example**:
```
DELETE /calendar/deleteEvent?userEventId=1
```

**Response**:
```json
{
  "status": "success",
  "message": "이벤트가 삭제되었습니다."
}
```

**React 예시**:
```jsx
const DeleteEvent = ({ eventId, onDeleted }) => {
  const handleDelete = async () => {
    if (!confirm('이벤트를 삭제하시겠습니까?')) return;

    try {
      const apiKey = localStorage.getItem('apiKey');

      await axios.delete('/calendar/deleteEvent', {
        headers: { 'X-API-Key': apiKey },
        params: { userEventId: eventId }
      });

      alert('이벤트가 삭제되었습니다.');
      onDeleted();

    } catch (error) {
      console.error('이벤트 삭제 실패:', error);
      alert('삭제에 실패했습니다.');
    }
  };

  return (
    <button onClick={handleDelete}>삭제</button>
  );
};
```

---

## 6. 마이페이지 API

### 6.1 마이페이지 데이터 조회

**Endpoint**: `GET /get/myPageData`

**Description**: 사용자 프로필 정보를 조회합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Response**:
```json
{
  "name": "홍길동",
  "birth": "1990-01-01",
  "homeAddress": "서울특별시 강남구 테헤란로 123",
  "centerAddress": "서울특별시 송파구 올림픽로 456",
  "linkCode": "LINKA123",
  "geofences": [
    {
      "id": 1,
      "name": "우리집",
      "address": "서울특별시 강남구 테헤란로 123",
      "type": 0,
      "startTime": null,
      "endTime": null
    }
  ]
}
```

**React 예시**:
```jsx
import { useEffect, useState } from 'react';
import axios from 'axios';

const MyPage = () => {
  const [userData, setUserData] = useState(null);

  useEffect(() => {
    fetchMyPageData();
  }, []);

  const fetchMyPageData = async () => {
    try {
      const apiKey = localStorage.getItem('apiKey');
      const response = await axios.get('/get/myPageData', {
        headers: { 'X-API-Key': apiKey }
      });
      setUserData(response.data);
    } catch (error) {
      console.error('마이페이지 조회 실패:', error);
    }
  };

  if (!userData) return <div>로딩중...</div>;

  return (
    <div>
      <h2>마이페이지</h2>
      <div>
        <p>이름: {userData.name}</p>
        <p>생년월일: {userData.birth}</p>
        <p>집 주소: {userData.homeAddress}</p>
        <p>센터 주소: {userData.centerAddress}</p>
        <p>내 링크 코드: {userData.linkCode}</p>
      </div>

      <section>
        <h3>내 지오펜스</h3>
        <ul>
          {userData.geofences.map(fence => (
            <li key={fence.id}>
              {fence.name} - {fence.address}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
};
```

---

### 6.2 비밀번호 변경

**Endpoint**: `PATCH /mypage/password`

**Description**: 사용자 비밀번호를 변경합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "currentPassword": "password123",
  "newPassword": "newPassword456"
}
```

**Response**:
```json
{
  "status": "success",
  "message": "비밀번호가 변경되었습니다."
}
```

**React 예시**:
```jsx
const ChangePassword = () => {
  const [passwords, setPasswords] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });

  const handleChangePassword = async (e) => {
    e.preventDefault();

    if (passwords.newPassword !== passwords.confirmPassword) {
      alert('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    try {
      const apiKey = localStorage.getItem('apiKey');

      await axios.patch('/mypage/password', {
        currentPassword: passwords.currentPassword,
        newPassword: passwords.newPassword
      }, {
        headers: { 'X-API-Key': apiKey }
      });

      alert('비밀번호가 변경되었습니다.');
      setPasswords({ currentPassword: '', newPassword: '', confirmPassword: '' });

    } catch (error) {
      console.error('비밀번호 변경 실패:', error);
      alert('비밀번호 변경에 실패했습니다.');
    }
  };

  return (
    <form onSubmit={handleChangePassword}>
      <input
        type="password"
        placeholder="현재 비밀번호"
        value={passwords.currentPassword}
        onChange={(e) => setPasswords({...passwords, currentPassword: e.target.value})}
      />
      <input
        type="password"
        placeholder="새 비밀번호"
        value={passwords.newPassword}
        onChange={(e) => setPasswords({...passwords, newPassword: e.target.value})}
      />
      <input
        type="password"
        placeholder="새 비밀번호 확인"
        value={passwords.confirmPassword}
        onChange={(e) => setPasswords({...passwords, confirmPassword: e.target.value})}
      />
      <button type="submit">변경</button>
    </form>
  );
};
```

---

### 6.3 집 주소 변경

**Endpoint**: `PATCH /mypage/homeAddress`

**Description**: 사용자의 집 주소를 변경합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "homeAddress": "12345",
  "homeStreetAddress": "서울특별시 강남구 테헤란로",
  "homeStreetAddressDetail": "101동 101호"
}
```

**Response**:
```json
{
  "status": "success",
  "message": "집 주소가 변경되었습니다."
}
```

**React 예시**:
```jsx
const UpdateHomeAddress = () => {
  const [address, setAddress] = useState({
    homeAddress: '',
    homeStreetAddress: '',
    homeStreetAddressDetail: ''
  });

  const handleUpdateAddress = async (e) => {
    e.preventDefault();

    try {
      const apiKey = localStorage.getItem('apiKey');

      await axios.patch('/mypage/homeAddress', address, {
        headers: { 'X-API-Key': apiKey }
      });

      alert('집 주소가 변경되었습니다.');

    } catch (error) {
      console.error('주소 변경 실패:', error);
      alert('주소 변경에 실패했습니다.');
    }
  };

  return (
    <form onSubmit={handleUpdateAddress}>
      <input
        type="text"
        placeholder="우편번호 (5자리)"
        value={address.homeAddress}
        onChange={(e) => setAddress({...address, homeAddress: e.target.value})}
      />
      <input
        type="text"
        placeholder="도로명 주소"
        value={address.homeStreetAddress}
        onChange={(e) => setAddress({...address, homeStreetAddress: e.target.value})}
      />
      <input
        type="text"
        placeholder="상세 주소"
        value={address.homeStreetAddressDetail}
        onChange={(e) => setAddress({...address, homeStreetAddressDetail: e.target.value})}
      />
      <button type="submit">주소 변경</button>
    </form>
  );
};
```

---

### 6.4 센터 주소 변경

**Endpoint**: `PATCH /mypage/centerAddress`

**Description**: 사용자의 센터 주소를 변경합니다.

**Headers**:
```
X-API-Key: your-api-key
```

**Request Body**:
```json
{
  "centerAddress": "67890",
  "centerStreetAddress": "서울특별시 송파구 올림픽로"
}
```

**Response**:
```json
{
  "status": "success",
  "message": "센터 주소가 변경되었습니다."
}
```

**React 예시**:
```jsx
const UpdateCenterAddress = () => {
  const [centerAddress, setCenterAddress] = useState({
    centerAddress: '',
    centerStreetAddress: ''
  });

  const handleUpdateCenterAddress = async (e) => {
    e.preventDefault();

    try {
      const apiKey = localStorage.getItem('apiKey');

      await axios.patch('/mypage/centerAddress', centerAddress, {
        headers: { 'X-API-Key': apiKey }
      });

      alert('센터 주소가 변경되었습니다.');

    } catch (error) {
      console.error('센터 주소 변경 실패:', error);
      alert('센터 주소 변경에 실패했습니다.');
    }
  };

  return (
    <form onSubmit={handleUpdateCenterAddress}>
      <input
        type="text"
        placeholder="우편번호 (5자리)"
        value={centerAddress.centerAddress}
        onChange={(e) => setCenterAddress({...centerAddress, centerAddress: e.target.value})}
      />
      <input
        type="text"
        placeholder="도로명 주소"
        value={centerAddress.centerStreetAddress}
        onChange={(e) => setCenterAddress({...centerAddress, centerStreetAddress: e.target.value})}
      />
      <button type="submit">센터 주소 변경</button>
    </form>
  );
};
```

---

## 7. 알림 (Notification) API

### 7.1 디바이스 토큰 등록

**Endpoint**: `POST /api/device-token/register`

**Description**: FCM 푸시 알림을 위한 디바이스 토큰을 등록합니다.

**Request Body**:
```json
{
  "userNumber": "01012345678",
  "token": "fcm_device_token_string_here",
  "deviceType": "android"
}
```

**Request 필드**:
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userNumber | String | ✅ | 사용자 전화번호 |
| token | String | ✅ | FCM 디바이스 토큰 |
| deviceType | String | ✅ | "android" 또는 "ios" |

**Response**:
```json
"토큰 등록 성공"
```

**Error Response**:
```json
"토큰 등록 실패: {error_message}"
```

**React Native 예시**:
```jsx
import messaging from '@react-native-firebase/messaging';
import axios from 'axios';

const RegisterDeviceToken = () => {
  const registerToken = async () => {
    try {
      // FCM 토큰 가져오기
      const fcmToken = await messaging().getToken();

      const userNumber = await AsyncStorage.getItem('userNumber');
      const deviceType = Platform.OS; // 'android' or 'ios'

      // 서버에 토큰 등록
      const response = await axios.post('/api/device-token/register', {
        userNumber: userNumber,
        token: fcmToken,
        deviceType: deviceType
      });

      console.log('✅ 디바이스 토큰 등록 성공:', response.data);

    } catch (error) {
      console.error('❌ 디바이스 토큰 등록 실패:', error);
    }
  };

  useEffect(() => {
    registerToken();

    // 토큰 갱신 감지
    const unsubscribe = messaging().onTokenRefresh(async (newToken) => {
      console.log('🔄 FCM 토큰 갱신됨');
      // 새 토큰으로 재등록
      await registerToken();
    });

    return unsubscribe;
  }, []);

  return null;
};
```

---

### 7.2 디바이스 토큰 삭제

**Endpoint**: `DELETE /api/device-token?token={fcm_token}`

**Description**: 로그아웃 시 디바이스 토큰을 삭제합니다.

**Query Parameters**:
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| token | String | ✅ | 삭제할 FCM 토큰 |

**Response**:
```json
"토큰 삭제 성공"
```

**React Native 예시**:
```jsx
const LogoutAndDeleteToken = () => {
  const handleLogout = async () => {
    try {
      const fcmToken = await messaging().getToken();

      // 서버에서 토큰 삭제
      await axios.delete(`/api/device-token`, {
        params: { token: fcmToken }
      });

      // 로컬 스토리지 클리어
      await AsyncStorage.clear();

      console.log('✅ 로그아웃 완료');

      // 로그인 화면으로 이동
      navigation.navigate('Login');

    } catch (error) {
      console.error('❌ 로그아웃 실패:', error);
    }
  };

  return (
    <TouchableOpacity onPress={handleLogout}>
      <Text>로그아웃</Text>
    </TouchableOpacity>
  );
};
```

---

### 7.3 긴급 알림 전송

**Endpoint**: `POST /notification/emergency`

**Description**: 연결된 모든 사용자에게 긴급 알림을 전송합니다.

**Request Body**:
```json
{
  "userNumber": "01012345678"
}
```

**Response**:
```json
"긴급 알림이 전송되었습니다."
```

**Error Response**:
```json
"사용자를 찾을 수 없습니다."
```

**React Native 예시**:
```jsx
import { Alert } from 'react-native';
import axios from 'axios';

const EmergencyButton = () => {
  const [isSending, setIsSending] = useState(false);

  const sendEmergencyAlert = async () => {
    Alert.alert(
      '긴급 알림',
      '연결된 모든 사용자에게 긴급 알림을 보내시겠습니까?',
      [
        {
          text: '취소',
          style: 'cancel'
        },
        {
          text: '전송',
          onPress: async () => {
            try {
              setIsSending(true);

              const userNumber = await AsyncStorage.getItem('userNumber');

              const response = await axios.post('/notification/emergency', {
                userNumber: userNumber
              });

              console.log('✅ 긴급 알림 전송:', response.data);

              Alert.alert(
                '전송 완료',
                '연결된 사용자들에게 긴급 알림이 전송되었습니다.',
                [{ text: '확인' }]
              );

            } catch (error) {
              console.error('❌ 긴급 알림 전송 실패:', error);
              Alert.alert('오류', '긴급 알림 전송에 실패했습니다.');
            } finally {
              setIsSending(false);
            }
          }
        }
      ]
    );
  };

  return (
    <TouchableOpacity
      style={{
        backgroundColor: '#ff0000',
        padding: 20,
        borderRadius: 50,
        alignItems: 'center',
        justifyContent: 'center'
      }}
      onPress={sendEmergencyAlert}
      disabled={isSending}
    >
      <Text style={{ color: 'white', fontSize: 18, fontWeight: 'bold' }}>
        {isSending ? '전송 중...' : '🚨 긴급 알림'}
      </Text>
    </TouchableOpacity>
  );
};
```

---

### 7.4 푸시 알림 수신 처리

**React Native 예시 (포그라운드 + 백그라운드)**:
```jsx
import messaging from '@react-native-firebase/messaging';
import { useEffect } from 'react';
import { Alert } from 'react-native';

const NotificationHandler = () => {
  useEffect(() => {
    // 포그라운드 알림 수신
    const unsubscribeForeground = messaging().onMessage(async (remoteMessage) => {
      console.log('📬 포그라운드 알림 수신:', remoteMessage);

      Alert.alert(
        remoteMessage.notification?.title || '알림',
        remoteMessage.notification?.body || '',
        [{ text: '확인' }]
      );
    });

    // 백그라운드 알림 클릭 처리
    messaging().onNotificationOpenedApp((remoteMessage) => {
      console.log('📬 백그라운드 알림 클릭:', remoteMessage);

      // 알림 타입에 따라 화면 이동
      if (remoteMessage.data?.type === 'emergency') {
        navigation.navigate('EmergencyDetail', {
          userNumber: remoteMessage.data.userNumber
        });
      }
    });

    // 앱이 종료된 상태에서 알림 클릭
    messaging()
      .getInitialNotification()
      .then((remoteMessage) => {
        if (remoteMessage) {
          console.log('📬 앱 종료 상태 알림 클릭:', remoteMessage);

          // 알림으로 앱 실행 시 처리
          if (remoteMessage.data?.type === 'emergency') {
            navigation.navigate('EmergencyDetail', {
              userNumber: remoteMessage.data.userNumber
            });
          }
        }
      });

    return () => {
      unsubscribeForeground();
    };
  }, []);

  return null;
};

export default NotificationHandler;
```

---

### 7.5 알림 권한 요청

**React Native 예시**:
```jsx
import messaging from '@react-native-firebase/messaging';
import { Alert, Platform } from 'react-native';

const RequestNotificationPermission = () => {
  const requestPermission = async () => {
    try {
      // iOS는 권한 요청 필요
      if (Platform.OS === 'ios') {
        const authStatus = await messaging().requestPermission();
        const enabled =
          authStatus === messaging.AuthorizationStatus.AUTHORIZED ||
          authStatus === messaging.AuthorizationStatus.PROVISIONAL;

        if (!enabled) {
          Alert.alert(
            '알림 권한',
            '알림을 받으려면 설정에서 알림 권한을 허용해주세요.',
            [{ text: '확인' }]
          );
          return false;
        }
      }

      console.log('✅ 알림 권한 승인됨');
      return true;

    } catch (error) {
      console.error('❌ 알림 권한 요청 실패:', error);
      return false;
    }
  };

  useEffect(() => {
    requestPermission();
  }, []);

  return null;
};
```

---

## 8. WebSocket 실시간 위치 공유 API

### 7.1 WebSocket 개요

**WebSocket Endpoint**: `ws://your-server/ws` (SockJS 지원)

**프로토콜**: STOMP over WebSocket

**특징**:
- 실시간 양방향 통신
- 2초 간격 위치 업데이트
- 단방향 링크 권한 검증 (A가 B를 링크하면 A가 B의 위치 구독 가능)
- Caffeine 캐시를 통한 메모리 관리
- 조건부 DB 저장 (100m 이상 이동 또는 1분 경과 시)

---

### 7.2 WebSocket 연결

**Connection Headers**:
```
userNumber: 01012345678
```

**React 예시 (SockJS + STOMP)**:
```jsx
import { useEffect, useRef, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const LocationTracking = () => {
  const stompClientRef = useRef(null);
  const [isConnected, setIsConnected] = useState(false);
  const [locations, setLocations] = useState({});

  useEffect(() => {
    connectWebSocket();

    return () => {
      disconnectWebSocket();
    };
  }, []);

  const connectWebSocket = () => {
    const userNumber = localStorage.getItem('userNumber');

    // SockJS 연결 생성
    const socket = new SockJS('http://localhost:8080/ws');

    // STOMP 클라이언트 생성
    const stompClient = new Client({
      webSocketFactory: () => socket,
      connectHeaders: {
        userNumber: userNumber
      },
      debug: (str) => {
        console.log('STOMP:', str);
      },
      onConnect: () => {
        console.log('WebSocket 연결 성공');
        setIsConnected(true);
      },
      onDisconnect: () => {
        console.log('WebSocket 연결 해제');
        setIsConnected(false);
      },
      onStompError: (frame) => {
        console.error('STOMP 에러:', frame);
      }
    });

    stompClient.activate();
    stompClientRef.current = stompClient;
  };

  const disconnectWebSocket = () => {
    if (stompClientRef.current) {
      stompClientRef.current.deactivate();
    }
  };

  return (
    <div>
      <p>연결 상태: {isConnected ? '연결됨' : '연결 안됨'}</p>
    </div>
  );
};
```

---

### 7.3 위치 업데이트 전송

**Destination**: `/app/location`

**Message Format**:
```json
{
  "latitude": 37.123456,
  "longitude": 127.123456
}
```

**주의사항**:
- `userNumber`는 서버에서 세션으로부터 자동 설정
- `timestamp`는 클라이언트에서 자동 설정 가능하지만 서버에서도 처리

**React 예시**:
```jsx
const LocationSender = () => {
  const stompClientRef = useRef(null);
  const [currentLocation, setCurrentLocation] = useState(null);

  useEffect(() => {
    // WebSocket 연결 (위의 connectWebSocket 함수 사용)
    connectWebSocket();

    // 위치 추적 시작
    startLocationTracking();

    return () => {
      stompClientRef.current?.deactivate();
    };
  }, []);

  const startLocationTracking = () => {
    // 브라우저 Geolocation API 사용
    if (!navigator.geolocation) {
      alert('위치 서비스를 지원하지 않는 브라우저입니다.');
      return;
    }

    // 2초마다 위치 전송
    const intervalId = setInterval(() => {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const locationData = {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude
          };

          setCurrentLocation(locationData);
          sendLocation(locationData);
        },
        (error) => {
          console.error('위치 조회 실패:', error);
        },
        {
          enableHighAccuracy: true,
          timeout: 5000,
          maximumAge: 0
        }
      );
    }, 2000); // 2초 간격

    return () => clearInterval(intervalId);
  };

  const sendLocation = (locationData) => {
    if (stompClientRef.current && stompClientRef.current.connected) {
      stompClientRef.current.publish({
        destination: '/app/location',
        body: JSON.stringify(locationData)
      });
      console.log('위치 전송:', locationData);
    }
  };

  return (
    <div>
      <h3>내 현재 위치</h3>
      {currentLocation ? (
        <p>
          위도: {currentLocation.latitude.toFixed(6)}<br/>
          경도: {currentLocation.longitude.toFixed(6)}
        </p>
      ) : (
        <p>위치 조회 중...</p>
      )}
    </div>
  );
};
```

---

### 7.4 다른 사용자 위치 구독

**Subscribe Destination**: `/topic/location/{targetUserNumber}`

**권한**: 현재 사용자가 대상 사용자를 링크로 등록한 경우에만 구독 가능

**Received Message Format**:
```json
{
  "userNumber": "01098765432",
  "latitude": 37.123456,
  "longitude": 127.123456,
  "timestamp": 1729843200000
}
```

**React 예시**:
```jsx
const FriendLocationTracker = ({ friendUserNumber }) => {
  const stompClientRef = useRef(null);
  const subscriptionRef = useRef(null);
  const [friendLocation, setFriendLocation] = useState(null);

  useEffect(() => {
    if (stompClientRef.current && stompClientRef.current.connected) {
      subscribeFriendLocation();
    }

    return () => {
      unsubscribeFriendLocation();
    };
  }, [friendUserNumber]);

  const subscribeFriendLocation = () => {
    if (!stompClientRef.current) return;

    // 친구 위치 구독
    subscriptionRef.current = stompClientRef.current.subscribe(
      `/topic/location/${friendUserNumber}`,
      (message) => {
        const locationData = JSON.parse(message.body);
        console.log('친구 위치 수신:', locationData);
        setFriendLocation(locationData);
      }
    );

    console.log(`${friendUserNumber} 위치 구독 시작`);
  };

  const unsubscribeFriendLocation = () => {
    if (subscriptionRef.current) {
      subscriptionRef.current.unsubscribe();
      console.log(`${friendUserNumber} 위치 구독 해제`);
    }
  };

  return (
    <div>
      <h3>{friendUserNumber}님의 위치</h3>
      {friendLocation ? (
        <div>
          <p>위도: {friendLocation.latitude.toFixed(6)}</p>
          <p>경도: {friendLocation.longitude.toFixed(6)}</p>
          <p>업데이트: {new Date(friendLocation.timestamp).toLocaleTimeString()}</p>
        </div>
      ) : (
        <p>위치 정보 대기 중...</p>
      )}
    </div>
  );
};
```

---

### 7.5 여러 친구 위치 동시 추적

**React 예시 (다중 구독)**:
```jsx
import { useEffect, useRef, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const MultipleLocationTracker = () => {
  const stompClientRef = useRef(null);
  const subscriptionsRef = useRef({});
  const [isConnected, setIsConnected] = useState(false);
  const [friends, setFriends] = useState([]);
  const [friendLocations, setFriendLocations] = useState({});

  useEffect(() => {
    // 친구 목록 가져오기
    fetchFriends();

    // WebSocket 연결
    connectWebSocket();

    return () => {
      disconnectWebSocket();
    };
  }, []);

  useEffect(() => {
    // 연결 성공 후 친구들 위치 구독
    if (isConnected && friends.length > 0) {
      subscribeAllFriends();
    }
  }, [isConnected, friends]);

  const fetchFriends = async () => {
    try {
      const apiKey = localStorage.getItem('apiKey');
      const response = await axios.get('/link/list', {
        headers: { 'X-API-Key': apiKey }
      });
      setFriends(response.data);
    } catch (error) {
      console.error('친구 목록 조회 실패:', error);
    }
  };

  const connectWebSocket = () => {
    const userNumber = localStorage.getItem('userNumber');
    const socket = new SockJS('http://localhost:8080/ws');

    const stompClient = new Client({
      webSocketFactory: () => socket,
      connectHeaders: {
        userNumber: userNumber
      },
      onConnect: () => {
        console.log('WebSocket 연결 성공');
        setIsConnected(true);
      },
      onDisconnect: () => {
        console.log('WebSocket 연결 해제');
        setIsConnected(false);
      }
    });

    stompClient.activate();
    stompClientRef.current = stompClient;
  };

  const subscribeAllFriends = () => {
    friends.forEach(friend => {
      const subscription = stompClientRef.current.subscribe(
        `/topic/location/${friend.userNumber}`,
        (message) => {
          const locationData = JSON.parse(message.body);

          setFriendLocations(prev => ({
            ...prev,
            [friend.userNumber]: locationData
          }));
        }
      );

      subscriptionsRef.current[friend.userNumber] = subscription;
      console.log(`${friend.userNumber} 구독 시작`);
    });
  };

  const disconnectWebSocket = () => {
    // 모든 구독 해제
    Object.values(subscriptionsRef.current).forEach(subscription => {
      subscription.unsubscribe();
    });

    // WebSocket 연결 해제
    if (stompClientRef.current) {
      stompClientRef.current.deactivate();
    }
  };

  return (
    <div>
      <h2>친구들 실시간 위치</h2>
      <p>연결 상태: {isConnected ? '✅ 연결됨' : '❌ 연결 안됨'}</p>

      <div>
        {friends.map(friend => (
          <div key={friend.id} style={{ border: '1px solid #ccc', padding: '10px', margin: '10px 0' }}>
            <h3>{friend.userNumber} ({friend.relation})</h3>
            {friendLocations[friend.userNumber] ? (
              <div>
                <p>📍 위도: {friendLocations[friend.userNumber].latitude.toFixed(6)}</p>
                <p>📍 경도: {friendLocations[friend.userNumber].longitude.toFixed(6)}</p>
                <p>🕐 업데이트: {new Date(friendLocations[friend.userNumber].timestamp).toLocaleString()}</p>
              </div>
            ) : (
              <p>위치 정보 대기 중...</p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
```

---

### 7.6 지도에 실시간 위치 표시 (카카오맵 예시)

**React + Kakao Map API 예시**:
```jsx
import { useEffect, useRef, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const RealTimeMapTracker = () => {
  const mapRef = useRef(null);
  const markersRef = useRef({});
  const stompClientRef = useRef(null);
  const [friends, setFriends] = useState([]);

  useEffect(() => {
    // 카카오맵 초기화
    initializeMap();

    // 친구 목록 가져오기
    fetchFriends();

    // WebSocket 연결
    connectWebSocket();

    return () => {
      stompClientRef.current?.deactivate();
    };
  }, []);

  const initializeMap = () => {
    const container = document.getElementById('map');
    const options = {
      center: new kakao.maps.LatLng(37.5665, 126.9780), // 서울 시청
      level: 5
    };
    mapRef.current = new kakao.maps.Map(container, options);
  };

  const fetchFriends = async () => {
    try {
      const apiKey = localStorage.getItem('apiKey');
      const response = await axios.get('/link/list', {
        headers: { 'X-API-Key': apiKey }
      });
      setFriends(response.data);
    } catch (error) {
      console.error('친구 목록 조회 실패:', error);
    }
  };

  const connectWebSocket = () => {
    const userNumber = localStorage.getItem('userNumber');
    const socket = new SockJS('http://localhost:8080/ws');

    const stompClient = new Client({
      webSocketFactory: () => socket,
      connectHeaders: { userNumber },
      onConnect: () => {
        console.log('WebSocket 연결 성공');
        subscribeLocations();
      }
    });

    stompClient.activate();
    stompClientRef.current = stompClient;
  };

  const subscribeLocations = () => {
    friends.forEach(friend => {
      stompClientRef.current.subscribe(
        `/topic/location/${friend.userNumber}`,
        (message) => {
          const locationData = JSON.parse(message.body);
          updateMarkerOnMap(friend, locationData);
        }
      );
    });
  };

  const updateMarkerOnMap = (friend, locationData) => {
    const position = new kakao.maps.LatLng(
      locationData.latitude,
      locationData.longitude
    );

    // 기존 마커가 있으면 위치 업데이트
    if (markersRef.current[friend.userNumber]) {
      markersRef.current[friend.userNumber].setPosition(position);
    } else {
      // 새 마커 생성
      const marker = new kakao.maps.Marker({
        position: position,
        map: mapRef.current
      });

      // 마커 클릭 시 정보 표시
      const infowindow = new kakao.maps.InfoWindow({
        content: `<div style="padding:5px;">${friend.userNumber}<br/>${friend.relation}</div>`
      });

      kakao.maps.event.addListener(marker, 'click', () => {
        infowindow.open(mapRef.current, marker);
      });

      markersRef.current[friend.userNumber] = marker;
    }

    // 지도 중심을 마지막 업데이트 위치로 이동
    mapRef.current.setCenter(position);
  };

  return (
    <div>
      <h2>실시간 위치 지도</h2>
      <div id="map" style={{ width: '100%', height: '600px' }}></div>
    </div>
  );
};
```

---

### 7.7 완전한 WebSocket 통합 예시

**모든 기능이 포함된 완전한 예시**:
```jsx
import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const CompleteLocationApp = () => {
  // State
  const [isConnected, setIsConnected] = useState(false);
  const [myLocation, setMyLocation] = useState(null);
  const [friends, setFriends] = useState([]);
  const [friendLocations, setFriendLocations] = useState({});

  // Refs
  const stompClientRef = useRef(null);
  const subscriptionsRef = useRef({});
  const locationIntervalRef = useRef(null);

  // 컴포넌트 마운트 시
  useEffect(() => {
    initialize();

    return () => {
      cleanup();
    };
  }, []);

  // WebSocket 연결 후 친구 구독
  useEffect(() => {
    if (isConnected && friends.length > 0) {
      subscribeAllFriends();
    }
  }, [isConnected, friends]);

  // 초기화
  const initialize = async () => {
    await fetchFriends();
    connectWebSocket();
    startSendingLocation();
  };

  // 정리
  const cleanup = () => {
    stopSendingLocation();
    unsubscribeAll();
    disconnectWebSocket();
  };

  // 친구 목록 가져오기
  const fetchFriends = async () => {
    try {
      const apiKey = localStorage.getItem('apiKey');
      const response = await axios.get('/link/list', {
        headers: { 'X-API-Key': apiKey }
      });
      setFriends(response.data);
    } catch (error) {
      console.error('친구 목록 조회 실패:', error);
    }
  };

  // WebSocket 연결
  const connectWebSocket = () => {
    const userNumber = localStorage.getItem('userNumber');
    const socket = new SockJS('http://localhost:8080/ws');

    const stompClient = new Client({
      webSocketFactory: () => socket,
      connectHeaders: {
        userNumber: userNumber
      },
      debug: (str) => console.log(str),
      onConnect: () => {
        console.log('✅ WebSocket 연결 성공');
        setIsConnected(true);
      },
      onDisconnect: () => {
        console.log('❌ WebSocket 연결 해제');
        setIsConnected(false);
      },
      onStompError: (frame) => {
        console.error('STOMP 에러:', frame.headers['message']);
      }
    });

    stompClient.activate();
    stompClientRef.current = stompClient;
  };

  // WebSocket 연결 해제
  const disconnectWebSocket = () => {
    if (stompClientRef.current) {
      stompClientRef.current.deactivate();
    }
  };

  // 모든 친구 위치 구독
  const subscribeAllFriends = () => {
    friends.forEach(friend => {
      const subscription = stompClientRef.current.subscribe(
        `/topic/location/${friend.userNumber}`,
        (message) => {
          const locationData = JSON.parse(message.body);

          setFriendLocations(prev => ({
            ...prev,
            [friend.userNumber]: locationData
          }));

          console.log(`📍 ${friend.userNumber} 위치 업데이트:`, locationData);
        }
      );

      subscriptionsRef.current[friend.userNumber] = subscription;
    });
  };

  // 모든 구독 해제
  const unsubscribeAll = () => {
    Object.values(subscriptionsRef.current).forEach(subscription => {
      subscription.unsubscribe();
    });
    subscriptionsRef.current = {};
  };

  // 내 위치 전송 시작
  const startSendingLocation = () => {
    if (!navigator.geolocation) {
      alert('이 브라우저는 위치 서비스를 지원하지 않습니다.');
      return;
    }

    // 2초마다 위치 전송
    locationIntervalRef.current = setInterval(() => {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const locationData = {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude
          };

          setMyLocation(locationData);
          sendMyLocation(locationData);
        },
        (error) => {
          console.error('위치 조회 실패:', error);
        },
        {
          enableHighAccuracy: true,
          timeout: 5000,
          maximumAge: 0
        }
      );
    }, 2000);
  };

  // 내 위치 전송 중지
  const stopSendingLocation = () => {
    if (locationIntervalRef.current) {
      clearInterval(locationIntervalRef.current);
    }
  };

  // 내 위치 서버로 전송
  const sendMyLocation = (locationData) => {
    if (stompClientRef.current && stompClientRef.current.connected) {
      stompClientRef.current.publish({
        destination: '/app/location',
        body: JSON.stringify(locationData)
      });
    }
  };

  // 거리 계산 (Haversine)
  const calculateDistance = (lat1, lon1, lat2, lon2) => {
    const R = 6371000; // 지구 반지름 (미터)
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a =
      Math.sin(dLat/2) * Math.sin(dLat/2) +
      Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
      Math.sin(dLon/2) * Math.sin(dLon/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    return R * c;
  };

  return (
    <div style={{ padding: '20px' }}>
      <h1>실시간 위치 공유 시스템</h1>

      {/* 연결 상태 */}
      <div style={{ marginBottom: '20px', padding: '10px', backgroundColor: isConnected ? '#d4edda' : '#f8d7da' }}>
        <strong>연결 상태:</strong> {isConnected ? '✅ 연결됨' : '❌ 연결 안됨'}
      </div>

      {/* 내 위치 */}
      <div style={{ marginBottom: '20px', padding: '15px', border: '2px solid #007bff', borderRadius: '5px' }}>
        <h2>내 현재 위치</h2>
        {myLocation ? (
          <div>
            <p>📍 위도: {myLocation.latitude.toFixed(6)}</p>
            <p>📍 경도: {myLocation.longitude.toFixed(6)}</p>
          </div>
        ) : (
          <p>위치 조회 중...</p>
        )}
      </div>

      {/* 친구들 위치 */}
      <div>
        <h2>친구들 위치 ({friends.length}명)</h2>
        {friends.length === 0 ? (
          <p>등록된 친구가 없습니다.</p>
        ) : (
          friends.map(friend => {
            const location = friendLocations[friend.userNumber];
            const distance = myLocation && location
              ? calculateDistance(
                  myLocation.latitude, myLocation.longitude,
                  location.latitude, location.longitude
                )
              : null;

            return (
              <div
                key={friend.id}
                style={{
                  border: '1px solid #ccc',
                  padding: '15px',
                  margin: '10px 0',
                  borderRadius: '5px',
                  backgroundColor: location ? '#f8f9fa' : '#fff'
                }}
              >
                <h3>{friend.userNumber} ({friend.relation})</h3>
                {location ? (
                  <div>
                    <p>📍 위도: {location.latitude.toFixed(6)}</p>
                    <p>📍 경도: {location.longitude.toFixed(6)}</p>
                    <p>🕐 업데이트: {new Date(location.timestamp).toLocaleString('ko-KR')}</p>
                    {distance !== null && (
                      <p style={{ fontWeight: 'bold', color: '#007bff' }}>
                        📏 거리: {distance >= 1000
                          ? `${(distance / 1000).toFixed(2)} km`
                          : `${Math.round(distance)} m`
                        }
                      </p>
                    )}
                  </div>
                ) : (
                  <p style={{ color: '#6c757d' }}>위치 정보 대기 중...</p>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default CompleteLocationApp;
```

---

## 8. 에러 처리

### 8.1 공통 에러 응답 형식

```json
{
  "status": "error",
  "message": "에러 메시지",
  "code": "ERROR_CODE"
}
```

### 8.2 주요 HTTP 상태 코드

- `200 OK`: 요청 성공
- `400 Bad Request`: 잘못된 요청 (유효성 검증 실패)
- `401 Unauthorized`: 인증 실패 (API 키 없음 또는 유효하지 않음)
- `403 Forbidden`: 권한 없음
- `404 Not Found`: 리소스를 찾을 수 없음
- `500 Internal Server Error`: 서버 내부 오류

### 8.3 WebSocket 에러

- 연결 실패: `userNumber` 헤더 누락
- 구독 실패: 권한 없음 (링크 관계가 없는 사용자 구독 시도)
- 메시지 전송 실패: 연결 끊김 또는 잘못된 메시지 형식

**React 에러 처리 예시**:
```jsx
// Axios 인터셉터로 공통 에러 처리
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response) {
      switch (error.response.status) {
        case 401:
          alert('로그인이 필요합니다.');
          localStorage.clear();
          window.location.href = '/login';
          break;
        case 403:
          alert('권한이 없습니다.');
          break;
        case 404:
          alert('요청한 리소스를 찾을 수 없습니다.');
          break;
        case 500:
          alert('서버 오류가 발생했습니다.');
          break;
        default:
          alert('오류가 발생했습니다: ' + error.response.data.message);
      }
    }
    return Promise.reject(error);
  }
);

// WebSocket 에러 처리
const stompClient = new Client({
  // ... 기타 설정
  onStompError: (frame) => {
    console.error('STOMP 에러:', frame);

    if (frame.headers.message.includes('권한')) {
      alert('해당 사용자의 위치를 볼 수 있는 권한이 없습니다.');
    } else if (frame.headers.message.includes('userNumber')) {
      alert('사용자 정보가 올바르지 않습니다.');
    }
  }
});
```

---

## 9. API 사용 예시 플로우

### 9.1 회원가입 → 로그인 → 위치 공유 전체 플로우

```jsx
import React, { useState } from 'react';
import axios from 'axios';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const CompleteFlow = () => {
  // 1단계: 회원가입
  const signUp = async (userData) => {
    try {
      await axios.post('/user/signup', userData);
      alert('회원가입 성공! 로그인해주세요.');
    } catch (error) {
      console.error('회원가입 실패:', error);
    }
  };

  // 2단계: 로그인
  const signIn = async (credentials) => {
    try {
      const response = await axios.post('/user/signIn', credentials);
      const { apiKey, userNumber, name } = response.data;

      // 로컬 스토리지에 저장
      localStorage.setItem('apiKey', apiKey);
      localStorage.setItem('userNumber', userNumber);
      localStorage.setItem('userName', name);

      // Axios 기본 헤더 설정
      axios.defaults.headers.common['X-API-Key'] = apiKey;

      return true;
    } catch (error) {
      console.error('로그인 실패:', error);
      return false;
    }
  };

  // 3단계: 친구 추가
  const addFriend = async (linkCode, relation) => {
    try {
      await axios.post('/link/addUser', { linkCode, relation });
      alert('친구 추가 완료!');
    } catch (error) {
      console.error('친구 추가 실패:', error);
    }
  };

  // 4단계: WebSocket 연결 및 위치 공유
  const startLocationSharing = () => {
    const userNumber = localStorage.getItem('userNumber');
    const socket = new SockJS('http://localhost:8080/ws');

    const stompClient = new Client({
      webSocketFactory: () => socket,
      connectHeaders: { userNumber },
      onConnect: () => {
        console.log('위치 공유 시작!');

        // 내 위치 2초마다 전송
        setInterval(() => {
          navigator.geolocation.getCurrentPosition(position => {
            stompClient.publish({
              destination: '/app/location',
              body: JSON.stringify({
                latitude: position.coords.latitude,
                longitude: position.coords.longitude
              })
            });
          });
        }, 2000);
      }
    });

    stompClient.activate();
  };

  return (
    <div>
      <h1>Safety Fence 사용 플로우</h1>
      {/* UI 구현 */}
    </div>
  );
};
```

---

## 10. 부록

### 10.1 필수 라이브러리 설치

**NPM 패키지**:
```bash
npm install axios sockjs-client @stomp/stompjs
```

**package.json**:
```json
{
  "dependencies": {
    "axios": "^1.6.0",
    "sockjs-client": "^1.6.1",
    "@stomp/stompjs": "^7.0.0",
    "react": "^18.2.0"
  }
}
```

### 10.2 환경 설정

**.env 파일**:
```
REACT_APP_API_URL=http://localhost:8080
REACT_APP_WS_URL=http://localhost:8080/ws
```

**Axios 기본 설정**:
```jsx
// src/api/axios.js
import axios from 'axios';

const instance = axios.create({
  baseURL: process.env.REACT_APP_API_URL,
  timeout: 10000
});

// 요청 인터셉터
instance.interceptors.request.use(
  config => {
    const apiKey = localStorage.getItem('apiKey');
    if (apiKey) {
      config.headers['X-API-Key'] = apiKey;
    }
    return config;
  },
  error => Promise.reject(error)
);

export default instance;
```

### 10.3 참고 문서

- **Spring Boot WebSocket**: https://spring.io/guides/gs/messaging-stomp-websocket/
- **STOMP.js**: https://stomp-js.github.io/stomp-websocket/
- **SockJS**: https://github.com/sockjs/sockjs-client
- **Axios**: https://axios-http.com/
- **Kakao Map API**: https://apis.map.kakao.com/

---

## 문서 정보

- **마지막 업데이트**: 2025-01-06
- **API 버전**: 1.1
- **작성자**: Safety Fence 개발팀
- **문의**: 개발팀 이메일

---

## 📝 변경 이력

### v1.1 (2025-01-06)
- **중요**: 인증 헤더 이름 수정 - `apiKey` → `X-API-Key`
- 백엔드 AuthInterceptor 구현에 맞춰 정확한 헤더 이름으로 변경
- 모든 API 예시 코드에서 헤더 사용법 업데이트
- Axios 인터셉터 예시 코드 수정

### v1.0 (2025-10-25)
- 최초 작성
- 전체 API 엔드포인트 문서화
- React/Axios 통합 예시 추가
- WebSocket 실시간 위치 공유 가이드 추가

---
